package com.yondex.archivo.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    onBack: () -> Unit,
    onCrop: (pageId: Long) -> Unit,
    viewModel: PageViewerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val title by viewModel.documentTitle.collectAsStateWithLifecycle()
    val busy by viewModel.busyPages.collectAsStateWithLifecycle()
    var showText by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.shareEvents.collect { context.startActivity(it) } }

    val list = pages
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LaunchedEffect(list.isEmpty()) { if (list.isEmpty()) onBack() }
    if (list.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = viewModel.initialIndex.coerceIn(0, list.lastIndex)) { list.size }
    val current = list.getOrNull(pagerState.currentPage) ?: list.last()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Halaman ${pagerState.currentPage + 1} dari ${list.size}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { showText = true },
                    icon = { Icon(Icons.AutoMirrored.Outlined.TextSnippet, contentDescription = null) },
                    label = { Text("Teks") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onCrop(current.id) },
                    icon = { Icon(Icons.Outlined.Crop, contentDescription = null) },
                    label = { Text("Potong") },
                )
                NavigationBarItem(
                    selected = false,
                    enabled = current.id !in busy,
                    onClick = { viewModel.rotate(current) },
                    icon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null) },
                    label = { Text("Putar") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { confirmDelete = true },
                    icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    label = { Text("Hapus") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pagerState, key = { list[it].id }, modifier = Modifier.fillMaxSize()) { index ->
                val page = list[index]
                ZoomableImage(
                    path = page.processedPath,
                    contentDescription = "Halaman ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (current.id in busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showText) {
        OcrTextSheet(
            page = current,
            onDismiss = { showText = false },
            onCopy = { text ->
                copyToClipboard(context, text)
                Toast.makeText(context, "Teks disalin", Toast.LENGTH_SHORT).show()
            },
            onShare = viewModel::shareText,
            onSave = { viewModel.saveText(current, it) },
            onRunOcr = { viewModel.runOcr(current) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus halaman?",
            message = "Halaman ${pagerState.currentPage + 1} akan dihapus permanen.",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete(current)
                confirmDelete = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrTextSheet(
    page: Page,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onSave: (String) -> Unit,
    onRunOcr: () -> Unit,
) {
    var editing by remember(page.id) { mutableStateOf(false) }
    var draft by remember(page.id, page.ocrText) { mutableStateOf(page.ocrText.orEmpty()) }
    val text = page.ocrText.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Teks halaman", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!editing && text.isNotEmpty()) {
                    IconButton(onClick = { onCopy(text) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin") }
                    IconButton(onClick = { onShare(text) }) { Icon(Icons.Outlined.Share, contentDescription = "Bagikan") }
                }
                if (!editing && page.ocrStatus == OcrStatus.DONE) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                }
            }
            Spacer(Modifier.padding(4.dp))
            when {
                editing -> {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        TextButton(onClick = { editing = false; draft = text }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onSave(draft); editing = false }) { Text("Simpan") }
                    }
                }
                page.ocrStatus == OcrStatus.PENDING || page.ocrStatus == OcrStatus.RUNNING -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(16.dp))
                        Text("Sedang mengenali teks…")
                    }
                }
                page.ocrStatus == OcrStatus.DONE && text.isNotEmpty() -> {
                    SelectionContainer(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    Text(
                        when (page.ocrStatus) {
                            OcrStatus.FAILED -> "Pengenalan teks gagal untuk halaman ini."
                            OcrStatus.DONE -> "Tidak ada teks yang terdeteksi."
                            else -> "Teks belum diekstrak dari halaman ini."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Button(onClick = onRunOcr) { Text("Jalankan OCR") }
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Archivo", text))
}
