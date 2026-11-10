package com.yondex.archivo.ui.crop

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.domain.model.NPoint
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.Quad
import com.yondex.archivo.scanner.BitmapIO
import com.yondex.archivo.scanner.EdgeDetector
import com.yondex.archivo.scanner.ImageProcessor
import com.yondex.archivo.scanner.ScanSession
import com.yondex.archivo.ui.navigation.CropRoute
import com.yondex.archivo.ui.navigation.NO_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CropUiState(
    val image: Bitmap? = null,
    val quad: Quad = Quad.FULL,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val rotation: Int = 0,
    val showPreview: Boolean = false,
    val preview: Bitmap? = null,
    val isWorking: Boolean = false,
    val loadFailed: Boolean = false,
)

private data class CropSource(
    val originalPath: String,
    val sessionPageId: String?,
    val pageId: Long,
    val documentId: Long,
)

@HiltViewModel
class CropViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: ScanSession,
    private val documentRepository: DocumentRepository,
    private val processor: ImageProcessor,
    private val detector: EdgeDetector,
    private val storage: FileStorage,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CropRoute>()
    private var source: CropSource? = null
    private var previewJob: Job? = null

    private val _state = MutableStateFlow(CropUiState())
    val state: StateFlow<CropUiState> = _state.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    init {
        viewModelScope.launch(Dispatchers.Default) { load() }
    }

    private suspend fun load() {
        try {
            val sessionPage = route.sessionPageId?.let(session::page)
            val (src, quad, rotation, filter) = when {
                sessionPage != null -> Quadruple(
                    CropSource(sessionPage.originalPath, sessionPage.id, NO_ID, NO_ID),
                    sessionPage.quad, sessionPage.rotation, sessionPage.filter,
                )
                route.pageId != NO_ID -> {
                    val page = documentRepository.getPage(route.pageId) ?: error("Page not found")
                    Quadruple(
                        CropSource(page.originalPath, null, page.id, page.documentId),
                        page.quad, page.rotation, page.filter,
                    )
                }
                else -> error("Nothing to crop")
            }
            source = src
            val image = BitmapIO.decodeFile(File(src.originalPath), DISPLAY_SIZE)
            _state.update { it.copy(image = image, quad = quad, rotation = rotation, filter = filter) }
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            _state.update { it.copy(loadFailed = true) }
        }
    }

    fun moveCorner(index: Int, point: NPoint) = _state.update { it.copy(quad = it.quad.withPoint(index, point)) }

    fun autoDetect() {
        val image = _state.value.image ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val quad = runCatching { detector.detect(image) }.getOrNull()
            _state.update { it.copy(quad = quad ?: Quad.FULL) }
            if (_state.value.showPreview) renderPreview()
        }
    }

    fun selectFull() {
        _state.update { it.copy(quad = Quad.FULL) }
        if (_state.value.showPreview) renderPreview()
    }

    fun setFilter(filter: PageFilter) {
        _state.update { it.copy(filter = filter) }
        if (_state.value.showPreview) renderPreview()
    }

    fun setPreview(show: Boolean) {
        _state.update { it.copy(showPreview = show) }
        if (show) renderPreview()
    }

    private fun renderPreview() {
        val current = _state.value
        val image = current.image ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            val preview = processor.process(image, current.quad, current.rotation, current.filter)
            _state.update { it.copy(preview = preview) }
        }
    }

    fun apply() {
        val src = source ?: return
        val current = _state.value
        if (current.isWorking) return
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (src.sessionPageId != null) {
                    val page = session.page(src.sessionPageId) ?: return@launch
                    val output = storage.newSessionFile("proc")
                    processor.process(File(src.originalPath), current.quad, current.rotation, current.filter, output)
                    session.replace(
                        page.copy(quad = current.quad, filter = current.filter, processedPath = output.absolutePath)
                    )
                } else {
                    val output = storage.newPageFile(src.documentId, "proc")
                    processor.process(File(src.originalPath), current.quad, current.rotation, current.filter, output)
                    documentRepository.updatePageImage(
                        src.pageId, output.absolutePath, current.quad, current.rotation, current.filter,
                    )
                }
                _done.emit(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Apply failed", e)
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private companion object {
        const val TAG = "CropViewModel"
        const val DISPLAY_SIZE = 1600
    }
}
