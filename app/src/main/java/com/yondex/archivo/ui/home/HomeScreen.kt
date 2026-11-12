package com.yondex.archivo.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.CallMerge
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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
import com.yondex.archivo.ui.components.TagDot
import com.yondex.archivo.ui.components.TagPickerDialog
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.components.Thumbnail
import com.yondex.archivo.ui.components.formatDate
import com.yondex.archivo.ui.label
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
    onQr: () -> Unit,
    onMerge: (documentIds: String, libraryFileIds: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var folderMenu by remember { mutableStateOf<Folder?>(null) }
    var libraryMenu by remember { mutableStateOf<LibraryFile?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importFiles(uris) }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = state.isSelecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "home_top_bar",
            ) { selecting ->
                if (selecting) {
                    SelectionTopBar(
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
                } else if (onBack != null) {
                    FolderTopBar(
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
                } else {
                    RootHomeTopBar(
                        viewMode = state.viewMode,
                        sortOrder = state.sortOrder,
                        onToggleView = {
                            viewModel.setViewMode(if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                        },
                        onSort = viewModel::setSortOrder,
                        onCreateFolder = { dialog = HomeDialog.CreateFolder },
                        onManageTags = onManageTags,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
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
                        GreetingHeader(
                            onImport = {
                                importLauncher.launch(arrayOf("application/pdf"))
                            },
                            onQr = onQr,
                        )
                    }
                    fullWidthItem("search") {
                        SearchEntry(onClick = onSearch, modifier = Modifier.padding(top = 2.dp))
                    }
                    fullWidthItem("categories") {
                        CategoryRow(
                            selectedFilter = state.filter,
                            onSelect = viewModel::setFilter,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    state.recentLibrary?.let { recent ->
                        if (state.filter == HomeFilter.ALL) {
                            fullWidthItem("recent") {
                                RecentlyAddedCard(
                                    file = recent,
                                    onClick = { onOpenPdf(recent.id) },
                                )
                            }
                        }
                    }
                }

                if (state.tags.isNotEmpty() && (state.filter == HomeFilter.ALL || state.filter == HomeFilter.SCANS)) {
                    fullWidthItem("tags") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(SketchChipGap),
                                modifier = Modifier.weight(1f),
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
                            IconButton(onClick = onManageTags) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_tag),
                                    contentDescription = stringResource(R.string.manage_tags),
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

                if (previewDocuments.isNotEmpty()) {
                    if (state.folders.isNotEmpty() || previewFiles.isNotEmpty() || state.tagFilter.isNotEmpty() || showMoreDocuments) {
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
                } else if (!state.isLoading && state.folders.isEmpty() && previewFiles.isEmpty()) {
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
        null -> Unit
    }
}

private const val ALL_PREVIEW_LIMIT = 6

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun GreetingHeader(
    onImport: () -> Unit,
    onQr: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
    val qrLabel = stringResource(R.string.nav_qr)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
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
        Spacer(Modifier.width(SketchChipGap))
        Surface(
            onClick = onQr,
            shape = SketchShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.semantics {
                contentDescription = qrLabel
                role = Role.Button
            },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qrcode),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(SketchChipGap))
        Surface(
            onClick = onImport,
            shape = SketchShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_upload),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.import_pdf),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
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
        modifier = modifier
            .fillMaxWidth()
            .semantics {
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
            )
        }
    }
}

@Composable
private fun CategoryRow(selectedFilter: HomeFilter, onSelect: (HomeFilter) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple(HomeFilter.ALL, R.string.filter_all, R.drawable.ic_home),
        Triple(HomeFilter.SCANS, R.string.filter_scans, R.drawable.ic_scanner),
        Triple(HomeFilter.PDF, R.string.filter_pdf, R.drawable.ic_document),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(SketchItemGap),
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEach { (filter, labelRes, iconRes) ->
            val active = filter == selectedFilter
            val label = stringResource(labelRes)
            Surface(
                onClick = { onSelect(filter) },
                shape = SketchShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        selected = active
                        role = Role.Tab
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedCard(file: LibraryFile, onClick: () -> Unit) {
    SketchCard(
        onClick = onClick,
        shape = SketchShapeLarge,
        paper = MaterialTheme.colorScheme.primary,
        selected = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recently_added),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    file.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = -90f },
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
private fun RootHomeTopBar(
    viewMode: ViewMode,
    sortOrder: SortOrder,
    onToggleView: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onCreateFolder: () -> Unit,
    onManageTags: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = {
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
        },
    )
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
                Icon(Icons.Sharp.CallMerge, contentDescription = stringResource(R.string.merge_action))
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
