package com.yondex.archivo.ui.document

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yondex.archivo.data.file.toSafeFileName
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.FolderPickerDialog
import com.yondex.archivo.ui.components.TagPickerDialog
import com.yondex.archivo.ui.components.TagRow
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.components.formatDate
import java.io.File

private enum class DocumentDialog { RENAME, MOVE, TAGS, DELETE, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    onBack: () -> Unit,
    onOpenPage: (index: Int) -> Unit,
    onCropPage: (pageId: Long) -> Unit,
    onAddPages: (documentId: Long) -> Unit,
    viewModel: DocumentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DocumentDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ExportOptions?>(null) }
    val document = state.document

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val options = pendingSave
        if (uri != null && options != null) viewModel.saveToUri(uri, options)
        pendingSave = null
    }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val options = pendingSave
        if (uri != null && options != null) viewModel.saveToUri(uri, options)
        pendingSave = null
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DocumentEvent.Launch -> try {
                    context.startActivity(event.intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Tidak ada aplikasi untuk membagikan", Toast.LENGTH_SHORT).show()
                }
                is DocumentEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                DocumentEvent.Deleted -> onBack()
            }
        }
    }
    LaunchedEffect(state.isLoading, document) {
        if (!state.isLoading && document == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    IconButton(onClick = { dialog = DocumentDialog.EXPORT }, enabled = state.pages.isNotEmpty()) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Lainnya") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuItem("Ganti nama", Icons.Outlined.Edit) { dialog = DocumentDialog.RENAME; menuOpen = false }
                            MenuItem("Pindahkan", Icons.AutoMirrored.Outlined.DriveFileMove) { dialog = DocumentDialog.MOVE; menuOpen = false }
                            MenuItem("Tag", Icons.AutoMirrored.Outlined.Label) { dialog = DocumentDialog.TAGS; menuOpen = false }
                            MenuItem("Jalankan OCR ulang", Icons.AutoMirrored.Outlined.TextSnippet) { viewModel.runOcr(); menuOpen = false }
                            MenuItem("Hapus", Icons.Outlined.Delete) { dialog = DocumentDialog.DELETE; menuOpen = false }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddPages(viewModel.documentId) },
                icon = { Icon(Icons.Outlined.AddAPhoto, contentDescription = null) },
                text = { Text("Tambah halaman") },
            )
        },
    ) { padding ->
        if (document == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${state.pages.size} halaman • ${state.folder?.name ?: "Beranda"} • ${formatDate(document.updatedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TagRow(document.tags, max = 6)
                    val ocrPending = state.pages.count { it.ocrStatus == OcrStatus.PENDING || it.ocrStatus == OcrStatus.RUNNING }
                    if (ocrPending > 0) {
                        Text(
                            "OCR sedang berjalan untuk $ocrPending halaman…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (state.pages.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.DocumentScanner,
                        title = "Dokumen kosong",
                        message = "Tambahkan halaman dengan memindai dokumen.",
                    )
                }
            }
            itemsIndexed(state.pages, key = { _, page -> page.id }) { index, page ->
                PageCard(
                    page = page,
                    number = index + 1,
                    isFirst = index == 0,
                    isLast = index == state.pages.lastIndex,
                    onOpen = { onOpenPage(index) },
                    onCrop = { onCropPage(page.id) },
                    onMove = { viewModel.movePage(page.id, it) },
                    onDelete = { viewModel.deletePage(page.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    when (dialog) {
        DocumentDialog.RENAME -> TextInputDialog(
            title = "Ganti nama",
            label = "Judul dokumen",
            initialValue = document?.title.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.rename(it); dialog = null },
        )
        DocumentDialog.MOVE -> FolderPickerDialog(
            folders = state.folders,
            currentFolderId = document?.folderId,
            onDismiss = { dialog = null },
            onPick = { viewModel.moveToFolder(it); dialog = null },
        )
        DocumentDialog.TAGS -> TagPickerDialog(
            tags = state.tags,
            initialSelected = document?.tags.orEmpty().map { it.id }.toSet(),
            onCreateTag = viewModel::createTag,
            onDismiss = { dialog = null },
            onConfirm = { added, removed -> viewModel.editTags(added, removed); dialog = null },
        )
        DocumentDialog.DELETE -> ConfirmDialog(
            title = "Hapus dokumen?",
            message = "\"${document?.title}\" dan semua halamannya akan dihapus permanen.",
            onDismiss = { dialog = null },
            onConfirm = { viewModel.delete(); dialog = null },
        )
        DocumentDialog.EXPORT -> ExportSheet(
            initial = ExportOptions(pageSize = state.settings.pdfPageSize, quality = state.settings.exportQuality),
            exporting = exporting,
            canSaveImages = viewModel.canSaveImagesToGallery,
            onDismiss = { dialog = null },
            onShare = { viewModel.share(it) },
            onSave = { options ->
                val name = document?.title.orEmpty().toSafeFileName()
                when (options.format) {
                    ExportFormat.PDF -> { pendingSave = options; savePdf.launch("$name.pdf") }
                    ExportFormat.TXT -> { pendingSave = options; saveText.launch("$name.txt") }
                    ExportFormat.JPG, ExportFormat.PNG -> viewModel.saveImagesToGallery(options)
                }
            },
        )
        null -> Unit
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCard(
    page: Page,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onCrop: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f).padding(6.dp)) {
                AsyncImage(
                    model = File(page.processedPath),
                    contentDescription = "Halaman $number",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
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
                OcrBadge(page.ocrStatus, Modifier.align(Alignment.TopEnd))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Potong & filter") }, onClick = { onCrop(); menuOpen = false })
            if (!isFirst) DropdownMenuItem(text = { Text("Geser ke depan") }, onClick = { onMove(-1); menuOpen = false })
            if (!isLast) DropdownMenuItem(text = { Text("Geser ke belakang") }, onClick = { onMove(1); menuOpen = false })
            DropdownMenuItem(text = { Text("Hapus halaman") }, onClick = { onDelete(); menuOpen = false })
        }
    }
}

@Composable
private fun OcrBadge(status: OcrStatus, modifier: Modifier = Modifier) {
    val (icon, description) = when (status) {
        OcrStatus.PENDING, OcrStatus.RUNNING -> Icons.Outlined.HourglassTop to "OCR berjalan"
        OcrStatus.DONE -> Icons.AutoMirrored.Outlined.TextSnippet to "Teks tersedia"
        OcrStatus.FAILED -> Icons.Outlined.ErrorOutline to "OCR gagal"
        OcrStatus.NONE -> return
    }
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = modifier) {
        Icon(icon, contentDescription = description, modifier = Modifier.padding(4.dp).size(16.dp))
    }
}
