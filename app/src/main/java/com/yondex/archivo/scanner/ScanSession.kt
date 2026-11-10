package com.yondex.archivo.scanner

import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.Quad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val originalPath: String,
    val processedPath: String,
    val quad: Quad,
    val rotation: Int,
    val filter: PageFilter,
)

/**
 * Holds pages captured during one scanning flow (scanner, review, crop) until they are saved
 * into a document or discarded.
 */
@Singleton
class ScanSession @Inject constructor(
    private val storage: FileStorage,
) {
    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    var targetDocumentId: Long = NO_ID
        private set
    var targetFolderId: Long = NO_ID
        private set

    fun start(documentId: Long, folderId: Long) {
        clear()
        targetDocumentId = documentId
        targetFolderId = folderId
    }

    fun page(id: String): ScanPage? = _pages.value.firstOrNull { it.id == id }

    fun add(page: ScanPage) = _pages.update { it + page }

    fun replace(page: ScanPage) {
        val previous = page(page.id)
        _pages.update { list -> list.map { if (it.id == page.id) page else it } }
        if (previous != null && previous.processedPath != page.processedPath) {
            storage.delete(previous.processedPath)
        }
    }

    fun remove(id: String) {
        val page = page(id) ?: return
        _pages.update { list -> list.filterNot { it.id == id } }
        storage.delete(page.originalPath)
        storage.delete(page.processedPath)
    }

    fun move(id: String, offset: Int) = _pages.update { list ->
        val from = list.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in list.indices) list
        else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Detaches pages after they were persisted, so [clear] does not delete their files. */
    fun detachAll(): List<ScanPage> {
        val current = _pages.value
        _pages.value = emptyList()
        return current
    }

    fun clear() {
        _pages.value = emptyList()
        storage.clearSession()
        targetDocumentId = NO_ID
        targetFolderId = NO_ID
    }

    companion object {
        const val NO_ID = -1L
    }
}
