package com.yondex.archivo.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.formatFileSize
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.HomeFilter
import com.yondex.archivo.domain.model.LibraryFile
import com.yondex.archivo.domain.model.SortOrder
import com.yondex.archivo.domain.model.ViewMode
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.DocumentGridCard
import com.yondex.archivo.ui.components.DocumentListItem
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.FolderItem
import com.yondex.archivo.ui.components.FolderPickerDialog
import com.yondex.archivo.ui.components.SketchCard
import com.yondex.archivo.ui.components.SketchChip
import com.yondex.archivo.ui.components.SketchDropdownMenu
import com.yondex.archivo.ui.components.SketchChipGap
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchShape
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.SketchSheetDragHandle
import com.yondex.archivo.ui.components.TagDot
import com.yondex.archivo.ui.components.TagPickerDialog
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.components.Thumbnail
import com.yondex.archivo.ui.components.formatDate
import com.yondex.archivo.ui.label
import com.yondex.archivo.ui.pdf.CompressPdfSheet
import java.util.Calendar

private sealed interface HomeDialog {
    data object CreateFolder : HomeDialog
    data class RenameFolder(val folder: Folder) : HomeDialog
    data class DeleteFolder(val folder: Folder) : HomeDialog
    data object MoveDocuments : HomeDialog
    data object TagDocuments : HomeDialog
    data object DeleteDocuments : HomeDialog
    data class RenameLibraryFile(val file: LibraryFile) : HomeDialog
    data class DeleteLibraryFile(val file: LibraryFile) : HomeDialog
    data class CompressLibraryFile(val file: LibraryFile) : HomeDialog
    data object MergeSplitTools : HomeDialog
    data class PickLibraryPdf(val purpose: PdfFeaturePurpose) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onBack: (() -> Unit)?,
    onOpenFolder: (Long) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenPdf: (Long) -> Unit,
    onSearch: () -> Unit,
    onManageTags: () -> Unit,
    onScan: () -> Unit,
    onMerge: (documentIds: String, libraryFileIds: String) -> Unit,
    onSplit: (libraryFileId: Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val compressing by viewModel.compressing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var folderMenu by remember { mutableStateOf<Folder?>(null) }
    var libraryMenu by remember { mutableStateOf<LibraryFile?>(null) }
    var pendingFeatureImport by remember { mutableStateOf<PdfFeaturePurpose?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importFiles(uris) }

    val featureImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val purpose = pendingFeatureImport
        pendingFeatureImport = null
        if (purpose != null) viewModel.importForFeature(uris, purpose)
    }

    fun launchFeatureImport(purpose: PdfFeaturePurpose) {
        pendingFeatureImport = purpose
        featureImportLauncher.launch(arrayOf("application/pdf"))
    }

    fun openFeaturePdfPicker(purpose: PdfFeaturePurpose) {
        if (state.libraryFiles.isEmpty()) {
            launchFeatureImport(purpose)
        } else {
            dialog = HomeDialog.PickLibraryPdf(purpose)
        }
    }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenPdf -> onOpenPdf(event.id)
                is HomeEvent.SplitPdf -> onSplit(event.id)
                is HomeEvent.MergePdfs -> onMerge("", event.libraryFileIds)
                is HomeEvent.CompressPdf -> dialog = HomeDialog.CompressLibraryFile(event.file)
            }
        }
    }
    var wasCompressing by remember { mutableStateOf(false) }
    LaunchedEffect(compressing) {
        if (wasCompressing && !compressing && dialog is HomeDialog.CompressLibraryFile) {
            dialog = null
        }
        wasCompressing = compressing
    }

    Scaffold(
        topBar = {
            when {
                state.isSelecting -> SelectionTopBar(
                    count = state.selectionCount,
                    canMerge = state.canMerge,
                    hasDocuments = state.selection.isNotEmpty(),
                    canDelete = state.canDeleteSelection,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onMerge = {
                        onMerge(viewModel.mergeDocumentIds(), viewModel.mergeLibraryIds())
                        viewModel.clearSelection()
                    },
                    onTag = { dialog = HomeDialog.TagDocuments },
                    onMove = { dialog = HomeDialog.MoveDocuments },
                    onDelete = { dialog = HomeDialog.DeleteDocuments },
                )
                onBack != null -> FolderTopBar(
                    title = state.currentFolder?.name.orEmpty(),
                    viewMode = state.viewMode,
                    sortOrder = state.sortOrder,
                    onBack = onBack,
                    onSearch = onSearch,
                    onToggleView = {
                        viewModel.setViewMode(if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                    },
                    onSort = viewModel::setSortOrder,
                    onCreateFolder = { dialog = HomeDialog.CreateFolder },
                    onManageTags = onManageTags,
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { padding ->
        val isGrid = state.viewMode == ViewMode.GRID
        val isAllTab = state.filter == HomeFilter.ALL
        val previewFiles = if (isAllTab) state.libraryFiles.take(ALL_PREVIEW_LIMIT) else state.libraryFiles
        val previewDocuments = if (isAllTab) state.documents.take(ALL_PREVIEW_LIMIT) else state.documents
        val showMoreFiles = isAllTab && state.libraryFiles.size > ALL_PREVIEW_LIMIT
        val showMoreDocuments = isAllTab && state.documents.size > ALL_PREVIEW_LIMIT
        LazyVerticalGrid(
                columns = if (isGrid) GridCells.Adaptive(156.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(SketchItemGap),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (onBack == null && !state.isSelecting) {
                    fullWidthItem("greeting") {
                        GreetingHeader()
                    }
                    fullWidthItem("features") {
                        FeatureHighlights(
                            onScan = onScan,
                            onReadPdf = { openFeaturePdfPicker(PdfFeaturePurpose.READ) },
                            onMergeSplit = { dialog = HomeDialog.MergeSplitTools },
                            onCompress = { openFeaturePdfPicker(PdfFeaturePurpose.COMPRESS) },
                        )
                    }
                    fullWidthItem("search") {
                        SearchSortRow(
                            sortOrder = state.sortOrder,
                            onSearch = onSearch,
                            onSort = viewModel::setSortOrder,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    fullWidthItem("categories") {
                        CategoryRow(
                            selectedFilter = state.filter,
                            viewMode = state.viewMode,
                            onSelect = viewModel::setFilter,
                            onToggleView = {
                                viewModel.setViewMode(
                                    if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID,
                                )
                            },
                            onCreateFolder = { dialog = HomeDialog.CreateFolder },
                            onManageTags = onManageTags,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }

                if (state.tags.isNotEmpty() && (state.filter == HomeFilter.ALL || state.filter == HomeFilter.SCANS)) {
                    fullWidthItem("tags") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(SketchChipGap),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(state.tags, key = { it.id }) { tag ->
                                SketchChip(
                                    selected = tag.id in state.tagFilter,
                                    onClick = { viewModel.toggleTagFilter(tag.id) },
                                    label = { Text(tag.name) },
                                    leadingIcon = { TagDot(Color(tag.color)) },
                                )
                            }
                        }
                    }
                }

                if (state.folders.isNotEmpty()) {
                    fullWidthItem("folders_header") { SectionHeader(stringResource(R.string.section_folders)) }
                    items(
                        state.folders,
                        key = { "folder_${it.folder.id}" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { entry ->
                        Box {
                            FolderItem(
                                folder = entry.folder,
                                documentCount = entry.documentCount,
                                onClick = { onOpenFolder(entry.folder.id) },
                                onLongClick = { folderMenu = entry.folder },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SketchDropdownMenu(
                                expanded = folderMenu?.id == entry.folder.id,
                                onDismissRequest = { folderMenu = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    onClick = { dialog = HomeDialog.RenameFolder(entry.folder); folderMenu = null },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_folder)) },
                                    onClick = { dialog = HomeDialog.DeleteFolder(entry.folder); folderMenu = null },
                                )
                            }
                        }
                    }
                }

                if (previewDocuments.isNotEmpty()) {
                    if (state.folders.isNotEmpty() || state.tagFilter.isNotEmpty() || showMoreDocuments || previewFiles.isNotEmpty()) {
                        fullWidthItem("docs_header") {
                            SectionHeader(
                                text = when {
                                    state.tagFilter.isNotEmpty() -> stringResource(R.string.section_documents_tagged)
                                    state.filter == HomeFilter.SCANS -> stringResource(R.string.filter_scans)
                                    else -> stringResource(R.string.section_documents)
                                },
                                onViewMore = if (showMoreDocuments) {
                                    { viewModel.setFilter(HomeFilter.SCANS) }
                                } else {
                                    null
                                },
                            )
                        }
                    } else if (onBack == null) {
                        fullWidthItem("docs_header") {
                            SectionHeader(
                                text = stringResource(R.string.section_all_documents),
                                onViewMore = if (showMoreDocuments) {
                                    { viewModel.setFilter(HomeFilter.SCANS) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    items(previewDocuments, key = { "doc_${it.id}" }) { document ->
                        val selected = document.id in state.selection
                        val onClick = {
                            if (state.isSelecting) viewModel.toggleSelection(document.id) else onOpenDocument(document.id)
                        }
                        if (isGrid) {
                            DocumentGridCard(document, selected, onClick, { viewModel.toggleSelection(document.id) })
                        } else {
                            DocumentListItem(document, selected, onClick, { viewModel.toggleSelection(document.id) })
                        }
                    }
                }

                if (previewFiles.isNotEmpty()) {
                    fullWidthItem("files_header") {
                        SectionHeader(
                            text = when {
                                state.filter == HomeFilter.PDF -> stringResource(R.string.filter_pdf)
                                else -> stringResource(R.string.section_files)
                            },
                            onViewMore = if (showMoreFiles) {
                                { viewModel.setFilter(HomeFilter.PDF) }
                            } else {
                                null
                            },
                        )
                    }
                    items(
                        previewFiles,
                        key = { "lib_${it.id}" },
                        span = { if (isGrid) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                    ) { file ->
                        Box {
                            val selected = file.id in state.pdfSelection
                            val onClick = {
                                if (state.isSelecting) viewModel.togglePdfSelection(file.id)
                                else onOpenPdf(file.id)
                            }
                            val onLongClick = { viewModel.togglePdfSelection(file.id) }
                            if (isGrid) {
                                LibraryFileGridCard(
                                    file = file,
                                    selected = selected,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    onMore = { libraryMenu = file },
                                )
                            } else {
                                LibraryFileRow(
                                    file = file,
                                    selected = selected,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    onMore = { libraryMenu = file },
                                )
                            }
                            SketchDropdownMenu(
                                expanded = libraryMenu?.id == file.id,
                                onDismissRequest = { libraryMenu = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.compress_action)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_compress),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        dialog = HomeDialog.CompressLibraryFile(file)
                                        libraryMenu = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_rename),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        dialog = HomeDialog.RenameLibraryFile(file)
                                        libraryMenu = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        dialog = HomeDialog.DeleteLibraryFile(file)
                                        libraryMenu = null
                                    },
                                )
                            }
                        }
                    }
                }

                if (previewDocuments.isEmpty() && previewFiles.isEmpty() &&
                    !state.isLoading && state.folders.isEmpty()
                ) {
                    fullWidthItem("empty") {
                        val (titleRes, messageRes, iconRes) = when {
                            state.tagFilter.isNotEmpty() -> Triple(
                                R.string.empty_no_documents_filter,
                                R.string.empty_no_documents_filter_message,
                                R.drawable.ic_tag,
                            )
                            state.filter == HomeFilter.SCANS -> Triple(
                                R.string.empty_scans_title,
                                R.string.empty_scans_message,
                                R.drawable.ic_scanner,
                            )
                            state.filter == HomeFilter.PDF -> Triple(
                                R.string.empty_pdf_title,
                                R.string.empty_pdf_message,
                                R.drawable.ic_document,
                            )
                            else -> Triple(
                                R.string.empty_home_title,
                                R.string.empty_home_message,
                                R.drawable.ic_upload,
                            )
                        }
                        val actionLabel = when {
                            state.tagFilter.isNotEmpty() -> null
                            state.filter == HomeFilter.PDF -> stringResource(R.string.import_pdf)
                            else -> stringResource(R.string.action_scan)
                        }
                        val onAction = when {
                            state.tagFilter.isNotEmpty() -> null
                            state.filter == HomeFilter.PDF -> {
                                { importLauncher.launch(arrayOf("application/pdf")) }
                            }
                            else -> onScan
                        }
                        EmptyState(
                            iconRes = iconRes,
                            title = stringResource(titleRes),
                            message = stringResource(messageRes),
                            modifier = Modifier.padding(top = 32.dp),
                            actionLabel = actionLabel,
                            onAction = onAction,
                        )
                    }
                }
            }
    }

    when (val current = dialog) {
        HomeDialog.CreateFolder -> TextInputDialog(
            title = stringResource(R.string.new_folder),
            label = stringResource(R.string.folder_name),
            confirmLabel = stringResource(R.string.action_create),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.createFolder(it); dialog = null },
        )
        is HomeDialog.RenameFolder -> TextInputDialog(
            title = stringResource(R.string.rename_folder),
            label = stringResource(R.string.folder_name),
            initialValue = current.folder.name,
            onDismiss = { dialog = null },
            onConfirm = { viewModel.renameFolder(current.folder.id, it); dialog = null },
        )
        is HomeDialog.DeleteFolder -> ConfirmDialog(
            title = stringResource(R.string.delete_folder_title),
            message = stringResource(R.string.delete_folder_message, current.folder.name),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.deleteFolder(current.folder.id); dialog = null },
        )
        HomeDialog.MoveDocuments -> FolderPickerDialog(
            folders = state.allFolders,
            currentFolderId = state.selectedDocuments.map { it.folderId }.distinct().singleOrNull(),
            onDismiss = { dialog = null },
            onPick = { viewModel.moveSelected(it); dialog = null },
        )
        HomeDialog.TagDocuments -> {
            val selectedDocs = state.selectedDocuments
            val tagSets = selectedDocs.map { doc -> doc.tags.map { it.id }.toSet() }
            val common = tagSets.reduceOrNull { acc, set -> acc intersect set }.orEmpty()
            val any = tagSets.flatten().toSet()
            TagPickerDialog(
                tags = state.tags,
                initialSelected = common,
                partial = any - common,
                onCreateTag = viewModel::createTag,
                onDismiss = { dialog = null },
                onConfirm = { added, removed -> viewModel.editSelectedTags(added, removed); dialog = null },
            )
        }
        HomeDialog.DeleteDocuments -> ConfirmDialog(
            title = stringResource(R.string.delete_selection_title, state.selectionCount),
            message = stringResource(R.string.delete_selection_message),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.deleteSelected(); dialog = null },
        )
        is HomeDialog.RenameLibraryFile -> TextInputDialog(
            title = stringResource(R.string.rename_library_file),
            label = stringResource(R.string.file_name),
            initialValue = current.file.title,
            onDismiss = { dialog = null },
            onConfirm = { viewModel.renameLibraryFile(current.file.id, it); dialog = null },
        )
        is HomeDialog.DeleteLibraryFile -> ConfirmDialog(
            title = stringResource(R.string.delete_library_file_title),
            message = stringResource(R.string.delete_library_file_message, current.file.title),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.deleteLibraryFile(current.file.id); dialog = null },
        )
        is HomeDialog.CompressLibraryFile -> {
            val latest = state.libraryFiles.firstOrNull { it.id == current.file.id } ?: current.file
            CompressPdfSheet(
                currentBytes = latest.sizeBytes,
                compressing = compressing,
                onDismiss = { if (!compressing) dialog = null },
                onCompress = { targetBytes ->
                    viewModel.compressLibraryFile(latest.id, targetBytes)
                },
            )
        }
        HomeDialog.MergeSplitTools -> MergeSplitToolsSheet(
            onDismiss = { dialog = null },
            onMerge = {
                dialog = null
                openFeaturePdfPicker(PdfFeaturePurpose.MERGE)
            },
            onSplit = {
                dialog = null
                openFeaturePdfPicker(PdfFeaturePurpose.SPLIT)
            },
        )
        is HomeDialog.PickLibraryPdf -> FeaturePdfPickerSheet(
            files = state.libraryFiles,
            purpose = current.purpose,
            onDismiss = { dialog = null },
            onImport = {
                dialog = null
                launchFeatureImport(current.purpose)
            },
            onPick = { file ->
                dialog = null
                when (current.purpose) {
                    PdfFeaturePurpose.READ -> onOpenPdf(file.id)
                    PdfFeaturePurpose.SPLIT -> onSplit(file.id)
                    PdfFeaturePurpose.COMPRESS -> dialog = HomeDialog.CompressLibraryFile(file)
                    PdfFeaturePurpose.MERGE -> Unit
                }
            },
            onPickMultiple = { files ->
                dialog = null
                if (files.size < 2) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.feature_merge_need_two),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onMerge("", files.joinToString(",") { it.id.toString() })
                }
            },
        )
        null -> Unit
    }
}

private const val ALL_PREVIEW_LIMIT = 4

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun GreetingHeader() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Text(
            greeting,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.greeting_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeatureHighlights(
    onScan: () -> Unit,
    onReadPdf: () -> Unit,
    onMergeSplit: () -> Unit,
    onCompress: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SketchItemGap),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
    ) {
        Text(
            stringResource(R.string.feature_tools),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SketchItemGap), modifier = Modifier.fillMaxWidth()) {
            FeatureToolCard(
                title = stringResource(R.string.feature_scan),
                subtitle = stringResource(R.string.feature_scan_desc),
                iconRes = R.drawable.ic_scanner,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onScan,
                modifier = Modifier.weight(1f),
            )
            FeatureToolCard(
                title = stringResource(R.string.feature_read_pdf),
                subtitle = stringResource(R.string.feature_read_pdf_desc),
                iconRes = R.drawable.ic_document,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onReadPdf,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SketchItemGap), modifier = Modifier.fillMaxWidth()) {
            FeatureToolCard(
                title = stringResource(R.string.feature_merge_split),
                subtitle = stringResource(R.string.feature_merge_split_desc),
                iconRes = R.drawable.ic_split,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onMergeSplit,
                modifier = Modifier.weight(1f),
            )
            FeatureToolCard(
                title = stringResource(R.string.feature_compress),
                subtitle = stringResource(R.string.feature_compress_desc),
                iconRes = R.drawable.ic_compress,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onCompress,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeatureToolCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = SketchShapeLarge,
        color = container,
        contentColor = content,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .height(118.dp)
            .semantics {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            },
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(SketchShape)
                    .background(content.copy(alpha = 0.12f)),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeSplitToolsSheet(
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SketchShapeLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SketchSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.feature_merge_split),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            FeatureActionRow(
                title = stringResource(R.string.merge_action),
                subtitle = stringResource(R.string.merge_message),
                iconRes = R.drawable.ic_merge,
                onClick = onMerge,
            )
            FeatureActionRow(
                title = stringResource(R.string.split_action),
                subtitle = stringResource(R.string.split_message),
                iconRes = R.drawable.ic_split,
                onClick = onSplit,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FeatureActionRow(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SketchShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(SketchShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturePdfPickerSheet(
    files: List<LibraryFile>,
    purpose: PdfFeaturePurpose,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onPick: (LibraryFile) -> Unit,
    onPickMultiple: (List<LibraryFile>) -> Unit,
) {
    val multi = purpose == PdfFeaturePurpose.MERGE
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SketchShapeLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SketchSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.feature_pick_pdf),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                items(files, key = { it.id }) { file ->
                    val isSelected = file.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SketchShape)
                            .clickable {
                                if (multi) {
                                    selected = if (isSelected) selected - file.id else selected + file.id
                                } else {
                                    onPick(file)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Thumbnail(
                            path = file.coverPath,
                            modifier = Modifier.size(44.dp).clip(SketchShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                file.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatFileSize(file.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (multi && isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FeatureActionRow(
                title = stringResource(R.string.import_pdf),
                subtitle = stringResource(R.string.feature_read_pdf_desc),
                iconRes = R.drawable.ic_upload,
                onClick = onImport,
            )
            if (multi) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = {
                        onPickMultiple(files.filter { it.id in selected })
                    },
                    enabled = selected.size >= 2,
                    shape = SketchShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    ) {
                        Text(
                            stringResource(R.string.merge_action),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SearchSortRow(
    sortOrder: SortOrder,
    onSearch: () -> Unit,
    onSort: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val sortLabel = stringResource(R.string.action_sort)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SketchChipGap),
        modifier = modifier.fillMaxWidth(),
    ) {
        SearchEntry(onClick = onSearch, modifier = Modifier.weight(1f))
        Box {
            Surface(
                onClick = { sortMenu = true },
                shape = SketchShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .size(56.dp)
                    .semantics {
                        contentDescription = sortLabel
                        role = Role.Button
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sort),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            SketchDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label()) },
                        leadingIcon = { RadioButton(selected = order == sortOrder, onClick = null) },
                        onClick = { onSort(order); sortMenu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val searchLabel = stringResource(R.string.search_documents_hint)
    SketchCard(
        onClick = onClick,
        shape = SketchShape,
        modifier = modifier.semantics {
            contentDescription = searchLabel
            role = Role.Button
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                searchLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    selectedFilter: HomeFilter,
    viewMode: ViewMode,
    onSelect: (HomeFilter) -> Unit,
    onToggleView: () -> Unit,
    onCreateFolder: () -> Unit,
    onManageTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        Triple(HomeFilter.ALL, R.string.filter_all, R.drawable.ic_home),
        Triple(HomeFilter.SCANS, R.string.filter_scans, R.drawable.ic_scanner),
        Triple(HomeFilter.PDF, R.string.filter_pdf, R.drawable.ic_document),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SketchChipGap),
            modifier = Modifier.weight(1f),
        ) {
            items(filters, key = { it.first.name }) { (filter, labelRes, iconRes) ->
                val active = filter == selectedFilter
                val label = stringResource(labelRes)
                SketchChip(
                    selected = active,
                    onClick = { onSelect(filter) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = label
                        selected = active
                        role = Role.Tab
                    },
                )
            }
        }
        IconButton(onClick = onToggleView) {
            Icon(
                painter = painterResource(
                    if (viewMode == ViewMode.GRID) R.drawable.ic_list else R.drawable.ic_grid,
                ),
                contentDescription = stringResource(R.string.change_view),
            )
        }
        IconButton(onClick = onCreateFolder) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_add),
                contentDescription = stringResource(R.string.new_folder),
            )
        }
        IconButton(onClick = onManageTags) {
            Icon(
                painter = painterResource(R.drawable.ic_tag),
                contentDescription = stringResource(R.string.manage_tags),
            )
        }
    }
}

@Composable
private fun LibraryFileGridCard(
    file: LibraryFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    SketchCard(
        selected = selected,
        shape = SketchShapeLarge,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Thumbnail(file.coverPath, Modifier.fillMaxSize())
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(22.dp)
                            .clip(SketchShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (file.pageCount > 0) {
                    Text(
                        stringResource(R.string.pages_short, file.pageCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(SketchShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Icon(
                        Icons.Sharp.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        tint = Color.White,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatFileSize(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LibraryFileRow(
    file: LibraryFile,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMore: () -> Unit,
) {
    SketchCard(
        selected = selected,
        shape = SketchShapeLarge,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Thumbnail(file.coverPath, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (file.pageCount > 0) append(stringResource(R.string.pages_short, file.pageCount)).append(" • ")
                        append(formatDate(file.createdAt))
                        append(" • ")
                        append(formatFileSize(file.sizeBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Sharp.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    onViewMore: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onViewMore != null) {
            Text(
                stringResource(R.string.view_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onViewMore)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTopBar(
    title: String,
    viewMode: ViewMode,
    sortOrder: SortOrder,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onCreateFolder: () -> Unit,
    onManageTags: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.action_search),
                )
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    painter = painterResource(
                        if (viewMode == ViewMode.GRID) R.drawable.ic_list else R.drawable.ic_grid,
                    ),
                    contentDescription = stringResource(R.string.change_view),
                )
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sort),
                        contentDescription = stringResource(R.string.action_sort),
                    )
                }
                SketchDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label()) },
                            leadingIcon = { RadioButton(selected = order == sortOrder, onClick = null) },
                            onClick = { onSort(order); sortMenu = false },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Sharp.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                SketchDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_folder)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder_add),
                                contentDescription = null,
                            )
                        },
                        onClick = { onCreateFolder(); moreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_tags)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = null,
                            )
                        },
                        onClick = { onManageTags(); moreMenu = false },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    canMerge: Boolean,
    hasDocuments: Boolean,
    canDelete: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMerge: () -> Unit,
    onTag: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Sharp.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Sharp.SelectAll, contentDescription = stringResource(R.string.action_select_all))
            }
            IconButton(onClick = onMerge, enabled = canMerge) {
                Icon(
                    painter = painterResource(R.drawable.ic_merge),
                    contentDescription = stringResource(R.string.merge_action),
                )
            }
            IconButton(onClick = onTag, enabled = hasDocuments) {
                Icon(
                    painter = painterResource(R.drawable.ic_tag),
                    contentDescription = stringResource(R.string.action_tag),
                )
            }
            IconButton(onClick = onMove, enabled = hasDocuments) {
                Icon(
                    painter = painterResource(R.drawable.ic_move),
                    contentDescription = stringResource(R.string.action_move),
                )
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        },
    )
}
