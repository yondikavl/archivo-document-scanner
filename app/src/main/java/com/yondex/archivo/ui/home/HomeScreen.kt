package com.yondex.archivo.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.SortOrder
import com.yondex.archivo.domain.model.ViewMode
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.DocumentGridCard
import com.yondex.archivo.ui.components.DocumentListItem
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.FolderItem
import com.yondex.archivo.ui.components.FolderPickerDialog
import com.yondex.archivo.ui.components.TagDot
import com.yondex.archivo.ui.components.TagPickerDialog
import com.yondex.archivo.ui.components.TextInputDialog

private sealed interface HomeDialog {
    data object CreateFolder : HomeDialog
    data class RenameFolder(val folder: Folder) : HomeDialog
    data class DeleteFolder(val folder: Folder) : HomeDialog
    data object MoveDocuments : HomeDialog
    data object TagDocuments : HomeDialog
    data object DeleteDocuments : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBack: (() -> Unit)?,
    onOpenFolder: (Long) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onScan: (folderId: Long?) -> Unit,
    onSearch: () -> Unit,
    onManageTags: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var folderMenu by remember { mutableStateOf<Folder?>(null) }

    BackHandler(enabled = state.isSelecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (state.isSelecting) {
                SelectionTopBar(
                    count = state.selection.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onTag = { dialog = HomeDialog.TagDocuments },
                    onMove = { dialog = HomeDialog.MoveDocuments },
                    onDelete = { dialog = HomeDialog.DeleteDocuments },
                )
            } else {
                HomeTopBar(
                    title = state.currentFolder?.name ?: "Archivo",
                    onBack = onBack,
                    viewMode = state.viewMode,
                    sortOrder = state.sortOrder,
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
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!state.isSelecting) {
                ExtendedFloatingActionButton(
                    onClick = { onScan(state.folderId) },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                    text = { Text("Scan") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        val isGrid = state.viewMode == ViewMode.GRID
        LazyVerticalGrid(
            columns = if (isGrid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isGrid) 12.dp else 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.tags.isNotEmpty()) {
                fullWidthItem("tags") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.tags, key = { it.id }) { tag ->
                            FilterChip(
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
                fullWidthItem("folders_header") { SectionHeader("Folder") }
                items(state.folders, key = { "folder_${it.folder.id}" }) { entry ->
                    Box {
                        FolderItem(
                            folder = entry.folder,
                            documentCount = entry.documentCount,
                            onClick = { onOpenFolder(entry.folder.id) },
                            onLongClick = { folderMenu = entry.folder },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = folderMenu?.id == entry.folder.id,
                            onDismissRequest = { folderMenu = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ganti nama") },
                                onClick = { dialog = HomeDialog.RenameFolder(entry.folder); folderMenu = null },
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus folder") },
                                onClick = { dialog = HomeDialog.DeleteFolder(entry.folder); folderMenu = null },
                            )
                        }
                    }
                }
            }

            if (state.documents.isNotEmpty()) {
                if (state.folders.isNotEmpty() || state.tagFilter.isNotEmpty()) {
                    fullWidthItem("docs_header") {
                        SectionHeader(if (state.tagFilter.isNotEmpty()) "Dokumen dengan tag terpilih" else "Dokumen")
                    }
                }
                items(state.documents, key = { "doc_${it.id}" }) { document ->
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
            } else if (!state.isLoading && state.folders.isEmpty()) {
                fullWidthItem("empty") {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = if (state.tagFilter.isNotEmpty()) "Tidak ada dokumen" else "Belum ada dokumen",
                        message = if (state.tagFilter.isNotEmpty()) "Tidak ada dokumen dengan semua tag terpilih."
                        else "Tekan tombol Scan untuk memindai dokumen pertama Anda.",
                        modifier = Modifier.padding(top = 64.dp),
                    )
                }
            }
        }
    }

    when (val current = dialog) {
        HomeDialog.CreateFolder -> TextInputDialog(
            title = "Folder baru",
            label = "Nama folder",
            confirmLabel = "Buat",
            onDismiss = { dialog = null },
            onConfirm = { viewModel.createFolder(it); dialog = null },
        )
        is HomeDialog.RenameFolder -> TextInputDialog(
            title = "Ganti nama folder",
            label = "Nama folder",
            initialValue = current.folder.name,
            onDismiss = { dialog = null },
            onConfirm = { viewModel.renameFolder(current.folder.id, it); dialog = null },
        )
        is HomeDialog.DeleteFolder -> ConfirmDialog(
            title = "Hapus folder?",
            message = "Folder \"${current.folder.name}\" beserta subfoldernya akan dihapus. Dokumen di dalamnya dipindahkan ke Beranda.",
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
            title = "Hapus ${state.selection.size} dokumen?",
            message = "Dokumen dan semua halamannya akan dihapus permanen.",
            onDismiss = { dialog = null },
            onConfirm = { viewModel.deleteSelected(); dialog = null },
        )
        null -> Unit
    }
}

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    title: String,
    onBack: (() -> Unit)?,
    viewMode: ViewMode,
    sortOrder: SortOrder,
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
        title = {
            Text(
                title,
                style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Cari") }
            IconButton(onClick = onToggleView) {
                Icon(
                    if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = "Ubah tampilan",
                )
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            leadingIcon = { RadioButton(selected = order == sortOrder, onClick = null) },
                            onClick = { onSort(order); sortMenu = false },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Lainnya") }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Folder baru") },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                        onClick = { onCreateFolder(); moreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Kelola tag") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                        onClick = { onManageTags(); moreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Pengaturan") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
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
        title = { Text("$count dipilih") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Batal") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = "Pilih semua") }
            IconButton(onClick = onTag) { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = "Tag") }
            IconButton(onClick = onMove) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Pindahkan") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus") }
        },
    )
}

val SortOrder.label: String
    get() = when (this) {
        SortOrder.UPDATED_DESC -> "Terakhir diubah"
        SortOrder.CREATED_DESC -> "Tanggal dibuat"
        SortOrder.NAME_ASC -> "Nama (A–Z)"
        SortOrder.PAGES_DESC -> "Jumlah halaman"
    }
