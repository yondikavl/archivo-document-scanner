package com.yondex.archivo.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.domain.model.Quad
import com.yondex.archivo.scanner.BitmapIO
import com.yondex.archivo.scanner.EdgeDetector
import com.yondex.archivo.scanner.ImageProcessor
import com.yondex.archivo.scanner.ScanPage
import com.yondex.archivo.R
import com.yondex.archivo.scanner.ScanSession
import com.yondex.archivo.ui.navigation.ScannerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val session: ScanSession,
    private val processor: ImageProcessor,
    private val storage: FileStorage,
    private val settings: SettingsRepository,
    val edgeDetector: EdgeDetector,
) : ViewModel() {

    val pages: StateFlow<List<ScanPage>> = session.pages

    private val _processingCount = MutableStateFlow(0)
    val processingCount: StateFlow<Int> = _processingCount.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    init {
        val route = savedStateHandle.toRoute<ScannerRoute>()
        session.start(route.documentId, route.folderId)
    }

    fun onCaptured(bitmap: Bitmap, liveQuad: Quad?) {
        launchProcessing {
            addPage(bitmap, liveQuad)
        }
    }

    fun onCaptureFailed(error: Throwable) {
        Log.w(TAG, "Capture failed", error)
        _messages.tryEmit(context.getString(R.string.error_capture))
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        launchProcessing {
            uris.forEach { uri ->
                runCatching { BitmapIO.decodeUri(context, uri, BitmapIO.MAX_STORED_DIMENSION) }
                    .onSuccess { addPage(it, null) }
                    .onFailure {
                        Log.w(TAG, "Import failed for $uri", it)
                        _messages.tryEmit(context.getString(R.string.error_import))
                    }
            }
        }
    }

    private suspend fun addPage(bitmap: Bitmap, liveQuad: Quad?) {
        val original = storage.newSessionFile("orig")
        BitmapIO.saveJpeg(bitmap, original)
        val quad = runCatching { edgeDetector.detect(bitmap) }.getOrNull() ?: liveQuad ?: Quad.FULL
        val filter = settings.current().defaultFilter
        val processed = storage.newSessionFile("proc")
        val result = processor.process(bitmap, quad, 0, filter)
        BitmapIO.saveJpeg(result, processed)
        result.recycle()
        bitmap.recycle()
        session.add(
            ScanPage(
                originalPath = original.absolutePath,
                processedPath = processed.absolutePath,
                quad = quad,
                rotation = 0,
                filter = filter,
            )
        )
    }

    private fun launchProcessing(block: suspend () -> Unit) {
        _processingCount.update { it + 1 }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                _messages.tryEmit(context.getString(R.string.error_process))
            } finally {
                _processingCount.update { it - 1 }
            }
        }
    }

    override fun onCleared() {
        session.clear()
    }

    private companion object {
        const val TAG = "ScannerViewModel"
    }
}
