package com.yondex.archivo.ui.review

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.scanner.ImageProcessor
import com.yondex.archivo.scanner.ScanPage
import com.yondex.archivo.scanner.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface ReviewEvent {
    data class Saved(val documentId: Long, val isNewDocument: Boolean) : ReviewEvent
    data class Error(val message: String) : ReviewEvent
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val session: ScanSession,
    private val processor: ImageProcessor,
    private val storage: FileStorage,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    val pages: StateFlow<List<ScanPage>> = session.pages

    private val _busyPages = MutableStateFlow<Set<String>>(emptySet())
    val busyPages: StateFlow<Set<String>> = _busyPages.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _events = MutableSharedFlow<ReviewEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReviewEvent> = _events

    val isAddingToExisting: Boolean get() = session.targetDocumentId != ScanSession.NO_ID

    val defaultTitle: String
        get() = "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())

    fun rotate(pageId: String) {
        val page = session.page(pageId) ?: return
        if (pageId in _busyPages.value) return
        _busyPages.update { it + pageId }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val rotation = (page.rotation + 90) % 360
                val output = storage.newSessionFile("proc")
                processor.process(File(page.originalPath), page.quad, rotation, page.filter, output)
                session.replace(page.copy(rotation = rotation, processedPath = output.absolutePath))
            } catch (e: Exception) {
                Log.e("ReviewViewModel", "Rotate failed", e)
                _events.tryEmit(ReviewEvent.Error("Gagal memutar halaman"))
            } finally {
                _busyPages.update { it - pageId }
            }
        }
    }

    fun remove(pageId: String) = session.remove(pageId)

    fun move(pageId: String, offset: Int) = session.move(pageId, offset)

    fun save(title: String?) {
        if (_saving.value || pages.value.isEmpty()) return
        _saving.value = true
        viewModelScope.launch {
            try {
                val targetDocument = session.targetDocumentId
                val folderId = session.targetFolderId.takeIf { it != ScanSession.NO_ID }
                val toSave = session.pages.value
                val documentId = withContext(Dispatchers.IO) {
                    if (targetDocument != ScanSession.NO_ID) {
                        documentRepository.addPages(targetDocument, toSave)
                        targetDocument
                    } else {
                        documentRepository.createDocument(title?.ifBlank { null } ?: defaultTitle, folderId, toSave)
                    }
                }
                session.detachAll()
                _events.emit(ReviewEvent.Saved(documentId, targetDocument == ScanSession.NO_ID))
            } catch (e: Exception) {
                Log.e("ReviewViewModel", "Save failed", e)
                _events.emit(ReviewEvent.Error("Gagal menyimpan dokumen"))
            } finally {
                _saving.value = false
            }
        }
    }
}
