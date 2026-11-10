package com.yondex.archivo.ui.page

import android.content.Intent
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.pdf.ExportManager
import com.yondex.archivo.scanner.ImageProcessor
import com.yondex.archivo.ui.navigation.PageViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val processor: ImageProcessor,
    private val storage: FileStorage,
    private val exportManager: ExportManager,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PageViewerRoute>()
    val initialIndex = route.initialIndex

    val pages: StateFlow<List<Page>?> = documentRepository.observePages(route.documentId)
        .map<List<Page>, List<Page>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val documentTitle: StateFlow<String> = documentRepository.observeDocument(route.documentId)
        .map { it?.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _busyPages = MutableStateFlow<Set<Long>>(emptySet())
    val busyPages: StateFlow<Set<Long>> = _busyPages.asStateFlow()

    private val _shareEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<Intent> = _shareEvents

    fun rotate(page: Page) {
        if (page.id in _busyPages.value) return
        _busyPages.update { it + page.id }
        viewModelScope.launch {
            try {
                val rotation = (page.rotation + 90) % 360
                val output = storage.newPageFile(page.documentId, "proc")
                withContext(Dispatchers.Default) {
                    processor.process(File(page.originalPath), page.quad, rotation, page.filter, output)
                }
                documentRepository.updatePageImage(page.id, output.absolutePath, page.quad, rotation, page.filter)
            } catch (e: Exception) {
                Log.e("PageViewerViewModel", "Rotate failed", e)
            } finally {
                _busyPages.update { it - page.id }
            }
        }
    }

    fun delete(page: Page) = viewModelScope.launch { documentRepository.deletePage(page.id) }

    fun saveText(page: Page, text: String) = viewModelScope.launch { documentRepository.updateOcrText(page.id, text) }

    fun runOcr(page: Page) = viewModelScope.launch { documentRepository.requestPageOcr(page.id) }

    fun shareText(text: String) {
        _shareEvents.tryEmit(exportManager.shareTextIntent(text, documentTitle.value))
    }
}
