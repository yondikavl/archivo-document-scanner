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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.sharp.Sort
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.DocumentScanner
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.PictureAsPdf
import androidx.compose.material.icons.sharp.Search
import androidx.compose.material.icons.sharp.SelectAll
import androidx.compose.material.icons.sharp.CreateNewFolder
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.automirrored.sharp.DriveFileMove
import androidx.compose.material.icons.sharp.Folder as FolderIcon
import androidx.compose.material.icons.sharp.GridView
import androidx.compose.material.icons.automirrored.sharp.Label
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.automirrored.sharp.ViewList
import androidx.compose.material.icons.sharp.UploadFile
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.yondex.archivo.ui.components.SketchFab
import com.yondex.archivo.ui.components.SketchIconButton
import com.yondex.archivo.ui.components.SketchChipGap
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchShadowOffset
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.SketchStrokeSelected
import com.yondex.archivo.ui.components.TagDot
import com.yondex.archivo.ui.components.TagPickerDialog
import com.yondex.archivo.ui.components.TextInputDialog
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
    data class DeleteLibraryFile(val file: LibraryFile) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBack: (() -> Unit)?,
    onOpenFolder: (Long) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenPdf: (Long) -> Unit,
    onScan: (folderId: Long?) -> Unit,
    onSearch: () -> Unit,
    onManageTags: () -> Unit,
    onSettings: () -> Unit,
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
    LaunchedEffect(Unit) {
        viewModel.imported.collect { file -> onOpenPdf(file.id) }
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
                        count = state.selection.size,
                        onClose = viewModel::clearSelection,
                        onSelectAll = viewModel::selectAll,
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
                        onSettings = onSettings,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!state.isSelecting) {
                SketchFab(onClick = { onScan(state.folderId) }) {
                    Icon(Icons.Sharp.DocumentScanner, contentDescription = stringResource(R.string.action_scan))
                }
            }
        },
    ) { padding ->
        val isGrid = state.viewMode == ViewMode.GRID
        val isAllTab = state.filter == HomeFilter.ALL
        val previewFiles = if (isAllTab) state.libraryFiles.take(ALL_PREVIEW_LIMIT) else state.libraryFiles
        val previewDocuments = if (isAllTab) state.documents.take(ALL_PREVIEW_LIMIT) else state.documents
        val showMoreFiles = isAllTab && state.libraryFiles.size > ALL_PREVIEW_LIMIT
        val showMoreDocuments = isAllTab && state.documents.size > ALL_PREVIEW_LIMIT
        LazyVerticalGrid(
                columns = if (isGrid) GridCells.Adaptive(156.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(SketchItemGap),
                verticalArrangement = Arrangement.spacedBy(SketchItemGap),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (onBack == null && !state.isSelecting) {
                    fullWidthItem("greeting") {
                        GreetingHeader(
                            onImport = {
                                importLauncher.launch(arrayOf("application/pdf"))
                            },
                            onManageTags = onManageTags,
                            onSettings = onSettings,
                        )
                    }
                    fullWidthItem("search") {
                        SearchEntry(onClick = onSearch, modifier = Modifier.padding(top = 4.dp, bottom = SketchItemGap))
                    }
                    fullWidthItem("categories") {
                        CategoryRow(
                            selected = state.filter,
                            onSelect = viewModel::setFilter,
                            modifier = Modifier.padding(bottom = SketchItemGap),
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
                                    Icons.AutoMirrored.Sharp.Label,
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
                        span = { GridItemSpan(maxLineSpan) },
                    ) { file ->
                        Box {
                            LibraryFileRow(
                                file = file,
                                onClick = { onOpenPdf(file.id) },
                                onMore = { libraryMenu = file },
                            )
                            SketchDropdownMenu(
                                expanded = libraryMenu?.id == file.id,
                                onDismissRequest = { libraryMenu = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
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
                        EmptyState(
                            icon = Icons.Sharp.UploadFile,
                            title = stringResource(
                                if (state.tagFilter.isNotEmpty()) R.string.empty_no_documents_filter
                                else R.string.empty_home_title,
                            ),
                            message = stringResource(
                                if (state.tagFilter.isNotEmpty()) R.string.empty_no_documents_filter_message
                                else R.string.empty_home_message,
                            ),
                            modifier = Modifier.padding(top = 32.dp),
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
            title = stringResource(R.string.delete_documents_title, state.selection.size),
            message = stringResource(R.string.delete_documents_message),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.deleteSelected(); dialog = null },
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

private const val ALL_PREVIEW_LIMIT = 3

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun GreetingHeader(
    onImport: () -> Unit,
    onManageTags: () -> Unit,
    onSettings: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(R.string.greeting_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SketchIconButton(
            icon = Icons.Sharp.UploadFile,
            contentDescription = stringResource(R.string.import_file),
            onClick = onImport,
        )
        Spacer(Modifier.width(SketchChipGap))
        SketchIconButton(
            icon = Icons.AutoMirrored.Sharp.Label,
            contentDescription = stringResource(R.string.manage_tags),
            onClick = onManageTags,
        )
        Spacer(Modifier.width(SketchChipGap))
        SketchIconButton(
            icon = Icons.Sharp.Settings,
            contentDescription = stringResource(R.string.action_settings),
            onClick = onSettings,
        )
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SketchCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Sharp.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.search_documents_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRow(selected: HomeFilter, onSelect: (HomeFilter) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        HomeFilter.ALL to R.string.filter_all,
        HomeFilter.SCANS to R.string.filter_scans,
        HomeFilter.PDF to R.string.filter_pdf,
    )
    val shape = RectangleShape
    LazyRow(horizontalArrangement = Arrangement.spacedBy(SketchItemGap), modifier = modifier) {
        items(items, key = { it.first.name }) { (filter, label) ->
            val active = filter == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp + SketchShadowOffset)) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(64.dp)
                            .background(SketchInk, shape),
                    )
                    Surface(
                        onClick = { onSelect(filter) },
                        shape = shape,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        contentColor = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(64.dp)
                            .border(if (active) SketchStrokeSelected else SketchStroke, SketchInk, shape),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            when (filter) {
                                HomeFilter.ALL -> Icon(
                                    Icons.Sharp.FolderIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                                HomeFilter.SCANS -> Icon(Icons.Sharp.DocumentScanner, null, Modifier.size(28.dp))
                                HomeFilter.PDF -> Icon(Icons.Sharp.PictureAsPdf, null, Modifier.size(28.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedCard(file: LibraryFile, onClick: () -> Unit) {
    SketchCard(
        onClick = onClick,
        paper = MaterialTheme.colorScheme.primary,
        ink = SketchInk,
        selected = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Icon(
                Icons.Sharp.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recently_added),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    file.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
            Icon(
                Icons.AutoMirrored.Sharp.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun LibraryFileRow(file: LibraryFile, onClick: () -> Unit, onMore: () -> Unit) {
    SketchCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RectangleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(SketchStroke, SketchInk, RectangleShape),
            ) {
                Icon(
                    Icons.Sharp.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDate(file.openedAt)} • ${formatFileSize(file.sizeBytes)}",
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
            .padding(top = SketchItemGap, bottom = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
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
    onSettings: () -> Unit,
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
                Icon(Icons.Sharp.Search, contentDescription = stringResource(R.string.action_search))
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Sharp.ViewList else Icons.Sharp.GridView,
                    contentDescription = stringResource(R.string.change_view),
                )
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.AutoMirrored.Sharp.Sort, contentDescription = stringResource(R.string.action_sort))
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
                        leadingIcon = { Icon(Icons.Sharp.CreateNewFolder, contentDescription = null) },
                        onClick = { onCreateFolder(); moreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_tags)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Sharp.Label, contentDescription = null) },
                        onClick = { onManageTags(); moreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_settings)) },
                        leadingIcon = { Icon(Icons.Sharp.Settings, contentDescription = null) },
                        onClick = { onSettings(); moreMenu = false },
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
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
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
            IconButton(onClick = onTag) {
                Icon(Icons.AutoMirrored.Sharp.Label, contentDescription = stringResource(R.string.action_tag))
            }
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Sharp.DriveFileMove, contentDescription = stringResource(R.string.action_move))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Sharp.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}
