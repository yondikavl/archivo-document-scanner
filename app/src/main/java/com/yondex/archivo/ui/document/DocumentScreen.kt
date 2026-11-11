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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.DriveFileMove
import androidx.compose.material.icons.automirrored.sharp.Label
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.AddAPhoto
import androidx.compose.material.icons.sharp.CallSplit
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.sharp.DocumentScanner
import androidx.compose.material.icons.sharp.Edit
import androidx.compose.material.icons.sharp.ErrorOutline
import androidx.compose.material.icons.sharp.HourglassTop
import androidx.compose.material.icons.sharp.Share
import androidx.compose.material.icons.automirrored.sharp.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yondex.archivo.R
import com.yondex.archivo.data.file.toSafeFileName
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.FolderPickerDialog
import com.yondex.archivo.ui.components.SketchCard
import com.yondex.archivo.ui.components.SketchDropdownMenu
import com.yondex.archivo.ui.components.SketchExtendedFab
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.TagPickerDialog
import com.yondex.archivo.ui.components.TagRow
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.components.formatDate
import com.yondex.archivo.ui.components.sketchBorder
import java.io.File

private enum class DocumentDialog { RENAME, MOVE, TAGS, DELETE, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    onBack: () -> Unit,
    onOpenPage: (index: Int) -> Unit,
    onCropPage: (pageId: Long) -> Unit,
    onAddPages: (documentId: Long) -> Unit,
    onSplit: (documentId: Long) -> Unit,
    viewModel: DocumentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DocumentDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ExportOptions?>(null) }
    val document = state.document
    val noShareApp = stringResource(R.string.no_share_app)

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
                    Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(document?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { dialog = DocumentDialog.EXPORT }, enabled = state.pages.isNotEmpty()) {
                        Icon(Icons.Sharp.Share, contentDescription = stringResource(R.string.action_export))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Sharp.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        SketchDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuItem(stringResource(R.string.rename_document), Icons.Sharp.Edit) {
                                dialog = DocumentDialog.RENAME; menuOpen = false
                            }
                            MenuItem(stringResource(R.string.action_move), Icons.AutoMirrored.Sharp.DriveFileMove) {
                                dialog = DocumentDialog.MOVE; menuOpen = false
                            }
                            MenuItem(stringResource(R.string.action_tag), Icons.AutoMirrored.Sharp.Label) {
                                dialog = DocumentDialog.TAGS; menuOpen = false
                            }
                            MenuItem(stringResource(R.string.rerun_ocr), Icons.AutoMirrored.Sharp.TextSnippet) {
                                viewModel.runOcr(); menuOpen = false
                            }
                            MenuItem(stringResource(R.string.split_action), Icons.Sharp.CallSplit) {
                                menuOpen = false
                                onSplit(viewModel.documentId)
                            }
                            MenuItem(stringResource(R.string.action_delete), Icons.Sharp.Delete) {
                                dialog = DocumentDialog.DELETE; menuOpen = false
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            SketchExtendedFab(
                onClick = { onAddPages(viewModel.documentId) },
                icon = { Icon(Icons.Sharp.AddAPhoto, contentDescription = null) },
                text = { Text(stringResource(R.string.add_pages)) },
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
            horizontalArrangement = Arrangement.spacedBy(SketchItemGap),
            verticalArrangement = Arrangement.spacedBy(SketchItemGap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            R.string.document_meta,
                            state.pages.size,
                            state.folder?.name ?: stringResource(R.string.home_label),
                            formatDate(document.updatedAt),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TagRow(document.tags, max = 6)
                    val ocrPending = state.pages.count { it.ocrStatus == OcrStatus.PENDING || it.ocrStatus == OcrStatus.RUNNING }
                    if (ocrPending > 0) {
                        Text(
                            stringResource(R.string.ocr_running_pages, ocrPending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (state.pages.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Sharp.DocumentScanner,
                        title = stringResource(R.string.empty_document_title),
                        message = stringResource(R.string.empty_document_message),
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
            title = stringResource(R.string.rename_document),
            label = stringResource(R.string.document_title_label),
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
            title = stringResource(R.string.delete_document_title),
            message = stringResource(R.string.delete_document_message, document?.title.orEmpty()),
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
        SketchCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .padding(8.dp)
                    .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
            ) {
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                OcrBadge(page.ocrStatus, Modifier.align(Alignment.TopEnd))
            }
        }
        SketchDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.crop_and_filter)) },
                onClick = { onCrop(); menuOpen = false },
            )
            if (!isFirst) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_forward)) },
                    onClick = { onMove(-1); menuOpen = false },
                )
            }
            if (!isLast) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_backward)) },
                    onClick = { onMove(1); menuOpen = false },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_page)) },
                onClick = { onDelete(); menuOpen = false },
            )
        }
    }
}

@Composable
private fun OcrBadge(status: OcrStatus, modifier: Modifier = Modifier) {
    val (icon, descriptionRes) = when (status) {
        OcrStatus.PENDING, OcrStatus.RUNNING -> Icons.Sharp.HourglassTop to R.string.ocr_running
        OcrStatus.DONE -> Icons.AutoMirrored.Sharp.TextSnippet to R.string.ocr_ready
        OcrStatus.FAILED -> Icons.Sharp.ErrorOutline to R.string.ocr_failed
        OcrStatus.NONE -> return
    }
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier.sketchBorder(RectangleShape, SketchInk, SketchStroke),
    ) {
        Icon(icon, contentDescription = stringResource(descriptionRes), modifier = Modifier.padding(4.dp).size(16.dp))
    }
}
