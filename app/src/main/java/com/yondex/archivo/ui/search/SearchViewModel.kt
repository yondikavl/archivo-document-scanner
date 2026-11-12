package com.yondex.archivo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.data.repository.FolderRepository
import com.yondex.archivo.data.repository.LibraryRepository
import com.yondex.archivo.data.repository.TagRepository
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.LibraryFile
import com.yondex.archivo.domain.model.LibraryFileType
import com.yondex.archivo.domain.model.SearchResult
import com.yondex.archivo.domain.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SearchUiState(
    val hasQuery: Boolean = false,
    val results: List<SearchResult> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val libraryRepository: LibraryRepository,
    folderRepository: FolderRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _folderFilter = MutableStateFlow<Long?>(null)
    val folderFilter: StateFlow<Long?> = _folderFilter.asStateFlow()

    private val _tagFilter = MutableStateFlow<Set<Long>>(emptySet())
    val tagFilter: StateFlow<Set<Long>> = _tagFilter.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<SearchUiState> = combine(
        _query.debounce(250),
        documentRepository.observeDocuments(),
        libraryRepository.observeAll(),
        _folderFilter,
        _tagFilter,
    ) { query, documents, library, folderId, tagIds ->
        SearchParams(query.trim(), documents, library, folderId, tagIds)
    }
        .mapLatest { params ->
            if (params.query.isEmpty()) return@mapLatest SearchUiState()
            val byId = params.documents.associateBy { it.id }
            val documentHits = documentRepository.search(params.query).mapNotNull { (id, snippet) ->
                val document = byId[id] ?: return@mapNotNull null
                if (params.folderId != null && document.folderId != params.folderId) return@mapNotNull null
                if (!document.tags.map { it.id }.containsAll(params.tagIds)) return@mapNotNull null
                SearchResult.DocumentHit(document, snippet)
            }
            // PDFs have no folder/tags — include only when those filters are inactive.
            val pdfHits = if (params.folderId == null && params.tagIds.isEmpty()) {
                params.library
                    .filter { it.type == LibraryFileType.PDF }
                    .filter { it.title.contains(params.query, ignoreCase = true) }
                    .sortedByDescending { it.createdAt }
                    .map { SearchResult.PdfHit(it) }
            } else {
                emptyList()
            }
            val results = (documentHits + pdfHits).sortedByDescending { hit ->
                when (hit) {
                    is SearchResult.DocumentHit -> hit.document.updatedAt
                    is SearchResult.PdfHit -> hit.file.createdAt
                }
            }
            SearchUiState(hasQuery = true, results = results)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFolderFilter(folderId: Long?) {
        _folderFilter.value = folderId
    }

    fun toggleTag(tagId: Long) = _tagFilter.update { if (tagId in it) it - tagId else it + tagId }

    private data class SearchParams(
        val query: String,
        val documents: List<com.yondex.archivo.domain.model.Document>,
        val library: List<LibraryFile>,
        val folderId: Long?,
        val tagIds: Set<Long>,
    )
}
