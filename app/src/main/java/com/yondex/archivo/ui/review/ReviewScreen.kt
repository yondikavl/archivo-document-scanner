package com.yondex.archivo.ui.review

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yondex.archivo.scanner.ScanPage
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.TextInputDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onCrop: (sessionPageId: String) -> Unit,
    onSaved: (documentId: Long, isNewDocument: Boolean) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val busy by viewModel.busyPages.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var askTitle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewEvent.Saved -> onSaved(event.documentId, event.isNewDocument)
                is ReviewEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tinjau ${pages.size} halaman") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = pages.isNotEmpty() && !saving && busy.isEmpty(),
                    onClick = { if (viewModel.isAddingToExisting) viewModel.save(null) else askTitle = true },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan")
                }
            }
        },
    ) { padding ->
        if (pages.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.DocumentScanner,
                title = "Belum ada halaman",
                message = "Kembali ke kamera untuk memindai halaman.",
                modifier = Modifier.padding(padding).padding(top = 64.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    ReviewPageCard(
                        page = page,
                        number = index + 1,
                        isFirst = index == 0,
                        isLast = index == pages.lastIndex,
                        busy = page.id in busy,
                        onCrop = { onCrop(page.id) },
                        onRotate = { viewModel.rotate(page.id) },
                        onDelete = { viewModel.remove(page.id) },
                        onMove = { viewModel.move(page.id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (askTitle) {
        TextInputDialog(
            title = "Simpan dokumen",
            label = "Judul dokumen",
            initialValue = viewModel.defaultTitle,
            onDismiss = { askTitle = false },
            onConfirm = {
                askTitle = false
                viewModel.save(it)
            },
        )
    }
}

@Composable
private fun ReviewPageCard(
    page: ScanPage,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    busy: Boolean,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).padding(8.dp)) {
            AsyncImage(
                model = File(page.processedPath),
                contentDescription = "Halaman $number",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    "$number",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        Column {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCrop, enabled = !busy) { Icon(Icons.Outlined.Crop, contentDescription = "Potong") }
                IconButton(onClick = onRotate, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Putar") }
                IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus") }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Geser ke kiri")
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Geser ke kanan")
                }
            }
        }
    }
}
