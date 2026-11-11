package com.yondex.archivo.data.repository

import androidx.room.withTransaction
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.local.db.DocumentDao
import com.yondex.archivo.data.local.db.DocumentEntity
import com.yondex.archivo.data.local.db.DocumentFtsEntity
import com.yondex.archivo.data.local.db.PageDao
import com.yondex.archivo.data.local.db.PageEntity
import com.yondex.archivo.data.local.db.ArchivoDatabase
import com.yondex.archivo.data.local.db.SearchDao
import com.yondex.archivo.data.local.db.toDomain
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.domain.model.Document
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.Quad
import com.yondex.archivo.ocr.OcrScheduler
import com.yondex.archivo.pdf.PdfRasterizer
import com.yondex.archivo.scanner.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class SplitMode {
    /** One new document containing every selected page. */
    EXTRACT_ONE,
    /** One new document per selected page. */
    EACH_PAGE,
}

sealed interface MergeSource {
    data class Document(val documentId: Long) : MergeSource
    data class Pdf(val libraryFileId: Long, val path: String) : MergeSource
}

@Singleton
class DocumentRepository @Inject constructor(
    private val database: ArchivoDatabase,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val searchDao: SearchDao,
    private val storage: FileStorage,
    private val settings: SettingsRepository,
    private val ocrScheduler: OcrScheduler,
) {
    fun observeDocuments(): Flow<List<Document>> =
        documentDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeDocument(id: Long): Flow<Document?> =
        documentDao.observe(id).map { it?.toDomain() }

    fun observePages(documentId: Long): Flow<List<Page>> =
        pageDao.observeByDocument(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun getDocument(id: Long): Document? {
        val entity = documentDao.get(id) ?: return null
        val pages = pageDao.getByDocument(id)
        return Document(
            id = entity.id,
            title = entity.title,
            folderId = entity.folderId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            pageCount = pages.size,
            coverPath = pages.minByOrNull { it.pageIndex }?.processedPath,
            tags = emptyList(),
        )
    }

    suspend fun getPages(documentId: Long): List<Page> =
        pageDao.getByDocument(documentId).map { it.toDomain() }

    suspend fun getPage(id: Long): Page? = pageDao.get(id)?.toDomain()

    suspend fun createDocument(title: String, folderId: Long?, pages: List<ScanPage>): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val id = documentDao.insert(
                DocumentEntity(title = title, folderId = folderId, createdAt = now, updatedAt = now)
            )
            insertPages(id, pages, startIndex = 0)
            refreshSearchIndex(id)
            id
        }

    suspend fun addPages(documentId: Long, pages: List<ScanPage>) = withContext(Dispatchers.IO) {
        val start = (pageDao.maxIndex(documentId) ?: -1) + 1
        insertPages(documentId, pages, start)
        documentDao.touch(documentId, System.currentTimeMillis())
        refreshSearchIndex(documentId)
    }

    /** Creates a new document by appending pages from each source in order. */
    suspend fun mergeOrdered(
        sources: List<MergeSource>,
        title: String,
        folderId: Long?,
    ): Long = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "Nothing to merge" }
        val now = System.currentTimeMillis()
        val id = documentDao.insert(
            DocumentEntity(title = title, folderId = folderId, createdAt = now, updatedAt = now),
        )
        val ocrStatus = if (settings.current().autoOcr) OcrStatus.PENDING else OcrStatus.NONE
        val entities = mutableListOf<PageEntity>()
        var index = 0

        sources.forEach { source ->
            when (source) {
                is MergeSource.Document -> {
                    pageDao.getByDocument(source.documentId).forEach { page ->
                        entities += copyPageEntity(page, documentId = id, pageIndex = index++, ocrStatus = ocrStatus)
                    }
                }
                is MergeSource.Pdf -> {
                    val pdf = File(source.path)
                    val count = PdfRasterizer.pageCount(pdf)
                    repeat(count) { pageIndex ->
                        val orig = storage.newPageFile(id, "orig")
                        val proc = storage.newPageFile(id, "proc")
                        PdfRasterizer.writeJpegPage(pdf, pageIndex, orig)
                        storage.copy(orig, proc)
                        entities += PageEntity(
                            documentId = id,
                            pageIndex = index++,
                            originalPath = orig.absolutePath,
                            processedPath = proc.absolutePath,
                            cropQuad = Quad.FULL.encode(),
                            rotation = 0,
                            filter = PageFilter.ORIGINAL,
                            ocrText = null,
                            ocrStatus = ocrStatus,
                        )
                    }
                }
            }
        }

        require(entities.isNotEmpty()) { "Merged document has no pages" }
        pageDao.insertAll(entities)
        refreshSearchIndex(id)
        if (ocrStatus == OcrStatus.PENDING) ocrScheduler.schedule()
        id
    }

    /**
     * Splits pages out of a scanned document into one or more new documents.
     * @return ids of the newly created documents
     */
    suspend fun splitDocument(
        documentId: Long,
        pageIds: List<Long>,
        mode: SplitMode,
        titlePrefix: String,
        removeFromSource: Boolean,
    ): List<Long> = withContext(Dispatchers.IO) {
        val sourcePages = pageDao.getByDocument(documentId)
        val selected = pageIds.mapNotNull { id -> sourcePages.firstOrNull { it.id == id } }
        require(selected.isNotEmpty()) { "No pages selected" }
        val groups: List<List<PageEntity>> = when (mode) {
            SplitMode.EXTRACT_ONE -> listOf(selected)
            SplitMode.EACH_PAGE -> selected.map { listOf(it) }
        }
        val ocrStatus = if (settings.current().autoOcr) OcrStatus.PENDING else OcrStatus.NONE
        val created = mutableListOf<Long>()
        database.withTransaction {
            groups.forEachIndexed { groupIndex, group ->
                val now = System.currentTimeMillis()
                val title = if (groups.size == 1) titlePrefix else "$titlePrefix (${groupIndex + 1})"
                val newId = documentDao.insert(
                    DocumentEntity(
                        title = title,
                        folderId = documentDao.get(documentId)?.folderId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                val copies = group.mapIndexed { pageIndex, page ->
                    copyPageEntity(page, documentId = newId, pageIndex = pageIndex, ocrStatus = ocrStatus)
                }
                pageDao.insertAll(copies)
                created += newId
            }
            if (removeFromSource) {
                val removeIds = selected.map { it.id }.toSet()
                removeIds.forEach { pageDao.delete(it) }
                val remaining = pageDao.getByDocument(documentId)
                pageDao.updateAll(remaining.mapIndexed { index, p -> p.copy(pageIndex = index) })
                documentDao.touch(documentId, System.currentTimeMillis())
            }
        }
        if (removeFromSource) {
            selected.forEach { page ->
                // Files were copied, so originals can be deleted only when removed from source.
                // copyPageEntity already copied; source paths still exist until we delete them here.
                storage.delete(page.originalPath)
                storage.delete(page.processedPath)
            }
            refreshSearchIndex(documentId)
        }
        created.forEach { refreshSearchIndex(it) }
        if (ocrStatus == OcrStatus.PENDING) ocrScheduler.schedule()
        created
    }

    /** Converts selected PDF page indices into a new scanned document. */
    suspend fun createDocumentFromPdf(
        libraryPath: String,
        pageIndices: List<Int>,
        title: String,
        folderId: Long?,
        mode: SplitMode,
    ): List<Long> = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "No pages selected" }
        val pdf = File(libraryPath)
        val groups: List<List<Int>> = when (mode) {
            SplitMode.EXTRACT_ONE -> listOf(pageIndices)
            SplitMode.EACH_PAGE -> pageIndices.map { listOf(it) }
        }
        val ocrStatus = if (settings.current().autoOcr) OcrStatus.PENDING else OcrStatus.NONE
        val created = mutableListOf<Long>()
        groups.forEachIndexed { groupIndex, group ->
            val now = System.currentTimeMillis()
            val docTitle = if (groups.size == 1) title else "$title (${groupIndex + 1})"
            val id = documentDao.insert(
                DocumentEntity(title = docTitle, folderId = folderId, createdAt = now, updatedAt = now),
            )
            val entities = group.mapIndexed { pageIndex, pdfPage ->
                val orig = storage.newPageFile(id, "orig")
                val proc = storage.newPageFile(id, "proc")
                PdfRasterizer.writeJpegPage(pdf, pdfPage, orig)
                storage.copy(orig, proc)
                PageEntity(
                    documentId = id,
                    pageIndex = pageIndex,
                    originalPath = orig.absolutePath,
                    processedPath = proc.absolutePath,
                    cropQuad = Quad.FULL.encode(),
                    rotation = 0,
                    filter = PageFilter.ORIGINAL,
                    ocrText = null,
                    ocrStatus = ocrStatus,
                )
            }
            pageDao.insertAll(entities)
            refreshSearchIndex(id)
            created += id
        }
        if (ocrStatus == OcrStatus.PENDING) ocrScheduler.schedule()
        created
    }

    private fun copyPageEntity(
        page: PageEntity,
        documentId: Long,
        pageIndex: Int,
        ocrStatus: OcrStatus,
    ): PageEntity {
        val original = storage.copy(File(page.originalPath), storage.newPageFile(documentId, "orig"))
        val processed = storage.copy(File(page.processedPath), storage.newPageFile(documentId, "proc"))
        return PageEntity(
            documentId = documentId,
            pageIndex = pageIndex,
            originalPath = original.absolutePath,
            processedPath = processed.absolutePath,
            cropQuad = page.cropQuad,
            rotation = page.rotation,
            filter = page.filter,
            ocrText = page.ocrText,
            ocrStatus = when {
                !page.ocrText.isNullOrBlank() -> OcrStatus.DONE
                ocrStatus == OcrStatus.PENDING -> OcrStatus.PENDING
                else -> page.ocrStatus
            },
        )
    }

    private suspend fun insertPages(documentId: Long, pages: List<ScanPage>, startIndex: Int) {
        val ocrStatus = if (settings.current().autoOcr) OcrStatus.PENDING else OcrStatus.NONE
        val entities = pages.mapIndexed { offset, page ->
            val original = storage.move(File(page.originalPath), storage.newPageFile(documentId, "orig"))
            val processed = storage.move(File(page.processedPath), storage.newPageFile(documentId, "proc"))
            PageEntity(
                documentId = documentId,
                pageIndex = startIndex + offset,
                originalPath = original.absolutePath,
                processedPath = processed.absolutePath,
                cropQuad = page.quad.encode(),
                rotation = page.rotation,
                filter = page.filter,
                ocrText = null,
                ocrStatus = ocrStatus,
            )
        }
        pageDao.insertAll(entities)
        if (ocrStatus == OcrStatus.PENDING) ocrScheduler.schedule()
    }

    suspend fun rename(documentId: Long, title: String) {
        documentDao.rename(documentId, title, System.currentTimeMillis())
        refreshSearchIndex(documentId)
    }

    suspend fun moveToFolder(documentIds: List<Long>, folderId: Long?) =
        documentDao.moveToFolder(documentIds, folderId)

    suspend fun delete(documentIds: List<Long>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            documentIds.forEach { searchDao.delete(it) }
            documentDao.delete(documentIds)
        }
        documentIds.forEach { storage.deleteDocument(it) }
    }

    suspend fun deletePage(pageId: Long) = withContext(Dispatchers.IO) {
        val page = pageDao.get(pageId) ?: return@withContext
        database.withTransaction {
            pageDao.delete(pageId)
            val remaining = pageDao.getByDocument(page.documentId)
            pageDao.updateAll(remaining.mapIndexed { index, p -> p.copy(pageIndex = index) })
            documentDao.touch(page.documentId, System.currentTimeMillis())
        }
        storage.delete(page.originalPath)
        storage.delete(page.processedPath)
        refreshSearchIndex(page.documentId)
    }

    suspend fun movePage(pageId: Long, offset: Int) {
        val page = pageDao.get(pageId) ?: return
        database.withTransaction {
            val pages = pageDao.getByDocument(page.documentId).toMutableList()
            val from = pages.indexOfFirst { it.id == pageId }
            val to = from + offset
            if (from < 0 || to !in pages.indices) return@withTransaction
            pages.add(to, pages.removeAt(from))
            pageDao.updateAll(pages.mapIndexed { index, p -> p.copy(pageIndex = index) })
            documentDao.touch(page.documentId, System.currentTimeMillis())
        }
    }

    /** Stores a freshly processed image for an existing page and queues OCR again. */
    suspend fun updatePageImage(
        pageId: Long,
        processedPath: String,
        quad: Quad,
        rotation: Int,
        filter: PageFilter,
    ) {
        val page = pageDao.get(pageId) ?: return
        val autoOcr = settings.current().autoOcr
        pageDao.update(
            page.copy(
                processedPath = processedPath,
                cropQuad = quad.encode(),
                rotation = rotation,
                filter = filter,
                ocrStatus = if (autoOcr) OcrStatus.PENDING else page.ocrStatus,
            )
        )
        documentDao.touch(page.documentId, System.currentTimeMillis())
        if (page.processedPath != processedPath) storage.delete(page.processedPath)
        if (autoOcr) ocrScheduler.schedule()
    }

    suspend fun updateOcrText(pageId: Long, text: String) {
        val page = pageDao.get(pageId) ?: return
        pageDao.setOcrResult(pageId, text, OcrStatus.DONE)
        refreshSearchIndex(page.documentId)
    }

    suspend fun requestOcr(documentId: Long) {
        pageDao.setOcrStatusForDocument(documentId, OcrStatus.PENDING)
        ocrScheduler.schedule()
    }

    suspend fun requestPageOcr(pageId: Long) {
        pageDao.setOcrStatus(pageId, OcrStatus.PENDING)
        ocrScheduler.schedule()
    }

    suspend fun refreshSearchIndex(documentId: Long) {
        val document = documentDao.get(documentId) ?: return
        val content = pageDao.getByDocument(documentId)
            .mapNotNull { it.ocrText?.takeIf(String::isNotBlank) }
            .joinToString("\n")
        database.withTransaction {
            searchDao.delete(documentId)
            searchDao.insert(DocumentFtsEntity(rowId = documentId, title = document.title, content = content))
        }
    }

    /** Returns matching document ids with a highlighted snippet, ordered by relevance of insertion. */
    suspend fun search(query: String): List<Pair<Long, String?>> {
        val match = buildMatchQuery(query) ?: return emptyList()
        return searchDao.search(match, HIGHLIGHT_START, HIGHLIGHT_END).map { it.documentId to it.snippet }
    }

    companion object {
        const val HIGHLIGHT_START = "\u0001"
        const val HIGHLIGHT_END = "\u0002"

        /** Turns free text into an FTS4 prefix query, e.g. "invoice 2026" -> "invoice* 2026*". */
        fun buildMatchQuery(raw: String): String? {
            // Lowercase so words like OR/AND/NOT/NEAR are not parsed as FTS operators.
            val tokens = raw.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
