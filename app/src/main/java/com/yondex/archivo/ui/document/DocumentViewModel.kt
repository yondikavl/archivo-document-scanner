package com.yondex.archivo.ui.document

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.data.repository.FolderRepository
import com.yondex.archivo.data.repository.TagRepository
import com.yondex.archivo.domain.model.AppSettings
import com.yondex.archivo.domain.model.Document
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.domain.model.Tag
import com.yondex.archivo.pdf.ExportManager
import com.yondex.archivo.pdf.ImageFormat
import com.yondex.archivo.ui.navigation.DocumentRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExportFormat(val label: String, val mimeType: String) {
    PDF("PDF", "application/pdf"),
    JPG("JPG", "image/jpeg"),
    PNG("PNG", "image/png"),
    TXT("Teks", "text/plain"),
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: ExportQuality = ExportQuality.MEDIUM,
)

data class DocumentUiState(
    val isLoading: Boolean = true,
    val document: Document? = null,
    val pages: List<Page> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val folder: Folder? get() = folders.firstOrNull { it.id == document?.folderId }
}

sealed interface DocumentEvent {
    data class Launch(val intent: Intent) : DocumentEvent
    data class Message(val text: String) : DocumentEvent
    data object Deleted : DocumentEvent
}

@HiltViewModel
class DocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val tagRepository: TagRepository,
    folderRepository: FolderRepository,
    settingsRepository: SettingsRepository,
    private val exportManager: ExportManager,
    private val storage: FileStorage,
) : ViewModel() {

    val documentId = savedStateHandle.toRoute<DocumentRoute>().documentId

    val uiState: StateFlow<DocumentUiState> = combine(
        documentRepository.observeDocument(documentId),
        documentRepository.observePages(documentId),
        tagRepository.observeTags(),
        folderRepository.observeFolders(),
        settingsRepository.settings,
    ) { document, pages, tags, folders, settings ->
        DocumentUiState(false, document, pages, tags, folders, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentUiState())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _events = MutableSharedFlow<DocumentEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DocumentEvent> = _events

    val canSaveImagesToGallery: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun rename(title: String) = viewModelScope.launch { documentRepository.rename(documentId, title) }

    fun moveToFolder(folderId: Long?) = viewModelScope.launch {
        documentRepository.moveToFolder(listOf(documentId), folderId)
    }

    fun editTags(added: Set<Long>, removed: Set<Long>) = viewModelScope.launch {
        tagRepository.editTags(listOf(documentId), added, removed)
    }

    fun createTag(name: String) = viewModelScope.launch {
        val color = TagRepository.PALETTE[uiState.value.tags.size % TagRepository.PALETTE.size]
        tagRepository.create(name, color)
    }

    fun runOcr() = viewModelScope.launch {
        documentRepository.requestOcr(documentId)
        _events.emit(DocumentEvent.Message("OCR dijalankan di latar belakang"))
    }

    fun delete() = viewModelScope.launch {
        documentRepository.delete(listOf(documentId))
        _events.emit(DocumentEvent.Deleted)
    }

    fun deletePage(pageId: Long) = viewModelScope.launch { documentRepository.deletePage(pageId) }

    fun movePage(pageId: Long, offset: Int) = viewModelScope.launch { documentRepository.movePage(pageId, offset) }

    fun share(options: ExportOptions) = export {
        val document = it.first
        val pages = it.second
        storage.clearExports()
        val intent = when (options.format) {
            ExportFormat.PDF -> exportManager.shareIntent(
                listOf(exportManager.createPdfFile(document, pages, options.pageSize, options.quality)),
                options.format.mimeType, document.title,
            )
            ExportFormat.JPG, ExportFormat.PNG -> exportManager.shareIntent(
                exportManager.createImageFiles(document, pages, options.format.toImageFormat()),
                options.format.mimeType, document.title,
            )
            ExportFormat.TXT -> exportManager.shareIntent(
                listOf(exportManager.createTextFile(document, pages)), options.format.mimeType, document.title,
            )
        }
        _events.emit(DocumentEvent.Launch(intent))
    }

    fun saveToUri(uri: Uri, options: ExportOptions) = export { (document, pages) ->
        exportManager.saveToUri(uri) { stream ->
            when (options.format) {
                ExportFormat.TXT -> exportManager.writeText(pages, stream)
                else -> exportManager.writePdf(document, pages, options.pageSize, options.quality, stream)
            }
        }
        _events.emit(DocumentEvent.Message("File tersimpan"))
    }

    fun saveImagesToGallery(options: ExportOptions) = export { (document, pages) ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val count = exportManager.saveImagesToGallery(document, pages, options.format.toImageFormat())
            _events.emit(DocumentEvent.Message("$count gambar disimpan ke Galeri › Pictures/Archivo"))
        }
    }

    private fun export(block: suspend (Pair<Document, List<Page>>) -> Unit) {
        val state = uiState.value
        val document = state.document ?: return
        if (state.pages.isEmpty() || _exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                block(document to state.pages)
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Export failed", e)
                _events.emit(DocumentEvent.Message("Export gagal: ${e.message}"))
            } finally {
                _exporting.value = false
            }
        }
    }

    private fun ExportFormat.toImageFormat() = if (this == ExportFormat.PNG) ImageFormat.PNG else ImageFormat.JPG
}
