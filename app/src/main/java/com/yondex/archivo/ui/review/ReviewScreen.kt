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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowRight
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material.icons.sharp.AddAPhoto
import androidx.compose.material.icons.sharp.Crop
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.automirrored.sharp.RotateRight
import androidx.compose.material.icons.sharp.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yondex.archivo.R
import com.yondex.archivo.scanner.ScanPage
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.SketchCard
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchOutlinedButton
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.components.sketchBorder
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title, pages.size)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .sketchBorder(RectangleShape, SketchInk, SketchStroke),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    SketchOutlinedButton(onClick = onBack) {
                        Icon(Icons.Sharp.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_add))
                    }
                    Spacer(Modifier.weight(1f))
                    SketchFilledButton(
                        enabled = pages.isNotEmpty() && !saving && busy.isEmpty(),
                        onClick = { if (viewModel.isAddingToExisting) viewModel.save(null) else askTitle = true },
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Sharp.Check, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        },
    ) { padding ->
        if (pages.isEmpty()) {
            EmptyState(
                icon = Icons.Sharp.DocumentScanner,
                title = stringResource(R.string.empty_review_title),
                message = stringResource(R.string.empty_review_message),
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
            title = stringResource(R.string.save_document),
            label = stringResource(R.string.document_title_label),
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
    SketchCard(modifier = modifier) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f).padding(8.dp)) {
                AsyncImage(
                    model = File(page.processedPath),
                    contentDescription = stringResource(R.string.page_cd, number),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RectangleShape),
                )
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .sketchBorder(RectangleShape, SketchInk, SketchStroke),
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
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCrop, enabled = !busy) {
                    Icon(Icons.Sharp.Crop, contentDescription = stringResource(R.string.action_crop))
                }
                IconButton(onClick = onRotate, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Sharp.RotateRight, contentDescription = stringResource(R.string.action_rotate))
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Sharp.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.AutoMirrored.Sharp.KeyboardArrowLeft, contentDescription = stringResource(R.string.move_forward))
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.AutoMirrored.Sharp.KeyboardArrowRight, contentDescription = stringResource(R.string.move_backward))
                }
            }
        }
    }
}
