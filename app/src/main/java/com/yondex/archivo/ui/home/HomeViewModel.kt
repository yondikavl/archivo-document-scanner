package com.yondex.archivo.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.data.repository.FolderRepository
import com.yondex.archivo.data.repository.TagRepository
import com.yondex.archivo.data.repository.pathTo
import com.yondex.archivo.domain.model.AppSettings
import com.yondex.archivo.domain.model.Document
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.SortOrder
import com.yondex.archivo.domain.model.Tag
import com.yondex.archivo.domain.model.ViewMode
import com.yondex.archivo.ui.navigation.HomeRoute
import com.yondex.archivo.ui.navigation.NO_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderEntry(val folder: Folder, val documentCount: Int)

data class HomeUiState(
    val isLoading: Boolean = true,
    val folderId: Long? = null,
    val path: List<Folder> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    val documents: List<Document> = emptyList(),
    val allFolders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagFilter: Set<Long> = emptySet(),
    val selection: Set<Long> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
) {
    val currentFolder: Folder? get() = path.lastOrNull()
    val isSelecting: Boolean get() = selection.isNotEmpty()
    val selectedDocuments: List<Document> get() = documents.filter { it.id in selection }
}

private data class LocalState(val tagFilter: Set<Long> = emptySet(), val selection: Set<Long> = emptySet())

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val folderId: Long? = savedStateHandle.toRoute<HomeRoute>().folderId.takeIf { it != NO_ID }
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        documentRepository.observeDocuments(),
        folderRepository.observeFolders(),
        tagRepository.observeTags(),
        settingsRepository.settings,
        local,
    ) { documents, folders, tags, settings, local ->
        buildState(documents, folders, tags, settings, local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(folderId = folderId))

    private fun buildState(
        documents: List<Document>,
        folders: List<Folder>,
        tags: List<Tag>,
        settings: AppSettings,
        local: LocalState,
    ): HomeUiState {
        val validTagFilter = local.tagFilter.filterTo(mutableSetOf()) { id -> tags.any { it.id == id } }
        val visible = if (validTagFilter.isNotEmpty()) {
            documents.filter { doc -> doc.tags.map { it.id }.containsAll(validTagFilter) }
        } else {
            documents.filter { it.folderId == folderId }
        }
        val counts = documents.groupingBy { it.folderId }.eachCount()
        val subfolders = if (validTagFilter.isEmpty()) {
            folders.filter { it.parentId == folderId }.map { FolderEntry(it, counts[it.id] ?: 0) }
        } else {
            emptyList()
        }
        val sorted = when (settings.sortOrder) {
            SortOrder.UPDATED_DESC -> visible.sortedByDescending { it.updatedAt }
            SortOrder.CREATED_DESC -> visible.sortedByDescending { it.createdAt }
            SortOrder.NAME_ASC -> visible.sortedBy { it.title.lowercase() }
            SortOrder.PAGES_DESC -> visible.sortedByDescending { it.pageCount }
        }
        val visibleIds = sorted.map { it.id }.toSet()
        return HomeUiState(
            isLoading = false,
            folderId = folderId,
            path = folders.pathTo(folderId),
            folders = subfolders,
            documents = sorted,
            allFolders = folders,
            tags = tags,
            tagFilter = validTagFilter,
            selection = local.selection.intersect(visibleIds),
            viewMode = settings.viewMode,
            sortOrder = settings.sortOrder,
        )
    }

    fun toggleTagFilter(tagId: Long) = local.update {
        it.copy(tagFilter = if (tagId in it.tagFilter) it.tagFilter - tagId else it.tagFilter + tagId, selection = emptySet())
    }

    fun clearTagFilter() = local.update { it.copy(tagFilter = emptySet()) }

    fun toggleSelection(documentId: Long) = local.update {
        it.copy(selection = if (documentId in it.selection) it.selection - documentId else it.selection + documentId)
    }

    fun selectAll() = local.update { it.copy(selection = uiState.value.documents.map { d -> d.id }.toSet()) }

    fun clearSelection() = local.update { it.copy(selection = emptySet()) }

    fun deleteSelected() = launchWithSelection { documentRepository.delete(it) }

    fun moveSelected(targetFolderId: Long?) = launchWithSelection { documentRepository.moveToFolder(it, targetFolderId) }

    fun editSelectedTags(added: Set<Long>, removed: Set<Long>) =
        launchWithSelection { tagRepository.editTags(it, added, removed) }

    fun createTag(name: String) = viewModelScope.launch {
        val color = TagRepository.PALETTE[uiState.value.tags.size % TagRepository.PALETTE.size]
        tagRepository.create(name, color)
    }

    fun createFolder(name: String) = viewModelScope.launch { folderRepository.create(name, folderId) }

    fun renameFolder(id: Long, name: String) = viewModelScope.launch { folderRepository.rename(id, name) }

    fun deleteFolder(id: Long) = viewModelScope.launch { folderRepository.delete(id) }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch { settingsRepository.setViewMode(mode) }

    fun setSortOrder(order: SortOrder) = viewModelScope.launch { settingsRepository.setSortOrder(order) }

    private fun launchWithSelection(block: suspend (List<Long>) -> Unit) {
        val ids = uiState.value.selection.toList()
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch { block(ids) }
    }
}
