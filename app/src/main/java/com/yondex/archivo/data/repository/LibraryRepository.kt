package com.yondex.archivo.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.file.toSafeFileName
import com.yondex.archivo.data.local.db.LibraryFileDao
import com.yondex.archivo.data.local.db.LibraryFileEntity
import com.yondex.archivo.domain.model.LibraryFile
import com.yondex.archivo.domain.model.LibraryFileType
import com.yondex.archivo.pdf.JpegPdfWriter
import com.yondex.archivo.pdf.PdfCompressResult
import com.yondex.archivo.pdf.PdfCompressor
import com.yondex.archivo.pdf.PdfImagePage
import com.yondex.archivo.pdf.PdfRasterizer
import com.yondex.archivo.scanner.BitmapIO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryFileDao,
    private val storage: FileStorage,
) {
    private val coverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll(): Flow<List<LibraryFile>> = dao.observeAll()
        .onEach { list ->
            list.filter { needsCover(it) }.forEach { entity ->
                coverScope.launch { ensureCover(entity.id) }
            }
        }
        .map { list ->
            list.map { it.toDomain() }.filter { it.type == LibraryFileType.PDF }
        }

    fun observeByType(type: LibraryFileType): Flow<List<LibraryFile>> =
        dao.observeByType(type.name).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): LibraryFile? = dao.get(id)?.toDomain()

    suspend fun import(uri: Uri): LibraryFile = withContext(Dispatchers.IO) {
        val meta = queryMeta(uri)
        val type = detectType(meta.name, meta.mime) ?: error("Unsupported file type")
        val safeName = meta.name.toSafeFileName()
        val target = storage.newLibraryFile(safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open file")
        val now = System.currentTimeMillis()
        val id = dao.insert(
            LibraryFileEntity(
                title = safeName.substringBeforeLast('.').ifBlank { safeName },
                type = type.name,
                path = target.absolutePath,
                sizeBytes = target.length(),
                createdAt = now,
                openedAt = now,
            ),
        )
        val cover = generateCover(id, target)
        LibraryFile(
            id = id,
            title = safeName.substringBeforeLast('.').ifBlank { safeName },
            type = type,
            path = target.absolutePath,
            sizeBytes = target.length(),
            createdAt = now,
            openedAt = now,
            coverPath = cover?.first,
            pageCount = cover?.second ?: 0,
        )
    }

    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    /**
     * Splits selected pages from a library PDF into one or more new library PDF files
     * (not scan documents).
     */
    suspend fun splitPdf(
        sourceId: Long,
        pageIndices: List<Int>,
        mode: SplitMode,
        titlePrefix: String,
    ): List<Long> = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "No pages selected" }
        val source = dao.get(sourceId) ?: error("PDF missing")
        val pdf = File(source.path)
        require(pdf.exists()) { "PDF file missing" }
        val groups: List<List<Int>> = when (mode) {
            SplitMode.EXTRACT_ONE -> listOf(pageIndices)
            SplitMode.EACH_PAGE -> pageIndices.map { listOf(it) }
        }
        val created = mutableListOf<Long>()
        groups.forEachIndexed { groupIndex, group ->
            val title = if (groups.size == 1) titlePrefix else "$titlePrefix (${groupIndex + 1})"
            val safeName = "${title.toSafeFileName()}.pdf"
            val target = storage.newLibraryFile(safeName)
            val imagePages = group.map { pageIndex ->
                val bitmap = PdfRasterizer.renderPage(pdf, pageIndex, BitmapIO.MAX_STORED_DIMENSION)
                try {
                    val jpeg = ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                        stream.toByteArray()
                    }
                    PdfImagePage(
                        pageWidth = bitmap.width.toFloat(),
                        pageHeight = bitmap.height.toFloat(),
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        jpeg = jpeg,
                    )
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            target.outputStream().use { output ->
                JpegPdfWriter.write(output, title, imagePages.size) { imagePages[it] }
            }
            val now = System.currentTimeMillis()
            val id = dao.insert(
                LibraryFileEntity(
                    title = title,
                    type = LibraryFileType.PDF.name,
                    path = target.absolutePath,
                    sizeBytes = target.length(),
                    createdAt = now,
                    openedAt = now,
                ),
            )
            generateCover(id, target)
            created += id
        }
        created
    }

    /**
     * Re-encodes a library PDF to meet [maxBytes] when possible (default 100 KB).
     * Overwrites the existing file in place and refreshes cover metadata.
     */
    suspend fun compressPdf(
        id: Long,
        maxBytes: Long = PdfCompressor.TARGET_100KB,
    ): Pair<LibraryFile, PdfCompressResult> = withContext(Dispatchers.IO) {
        val entity = dao.get(id) ?: error("PDF missing")
        val source = File(entity.path)
        require(source.exists()) { "PDF file missing" }
        val temp = File(source.parentFile, "compress_${id}_${System.nanoTime()}.pdf")
        try {
            val result = PdfCompressor.compress(
                source = source,
                title = entity.title,
                output = temp,
                targetBytes = maxBytes,
            )
            storage.move(temp, source)
            val now = System.currentTimeMillis()
            dao.updateSize(id, source.length(), now)
            generateCover(id, source)
            val updated = dao.get(id)?.toDomain() ?: error("PDF missing after compress")
            updated to result.copy(bytes = source.length())
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    suspend fun rename(id: Long, title: String) {
        val cleaned = title.trim()
        if (cleaned.isEmpty()) return
        dao.rename(id, cleaned)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.get(id) ?: return@withContext
        storage.delete(entity.coverPath)
        storage.deleteLibraryCover(id)
        storage.deleteLibraryFile(entity.path)
        dao.delete(id)
    }

    private suspend fun ensureCover(id: Long) {
        val entity = dao.get(id) ?: return
        if (!needsCover(entity)) return
        generateCover(id, File(entity.path))
    }

    private fun needsCover(entity: LibraryFileEntity): Boolean {
        if (entity.type != LibraryFileType.PDF.name) return false
        val cover = entity.coverPath
        return cover.isNullOrBlank() || !File(cover).exists() || entity.pageCount <= 0
    }

    private suspend fun generateCover(id: Long, pdf: File): Pair<String, Int>? {
        return runCatching {
            val pageCount = PdfRasterizer.pageCount(pdf)
            val cover = storage.libraryCoverFile(id)
            PdfRasterizer.writeJpegPage(
                file = pdf,
                index = 0,
                target = cover,
                maxDimension = COVER_MAX_DIMENSION,
                quality = 82,
            )
            dao.updateCover(id, cover.absolutePath, pageCount)
            cover.absolutePath to pageCount
        }.onFailure {
            Log.w(TAG, "Failed to generate PDF cover for $id", it)
        }.getOrNull()
    }

    private data class Meta(val name: String, val mime: String?)

    private fun queryMeta(uri: Uri): Meta {
        var name = "document"
        var mime = context.contentResolver.getType(uri)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return Meta(name, mime)
    }

    private fun detectType(name: String, mime: String?): LibraryFileType? {
        val lower = name.lowercase()
        return if (mime == "application/pdf" || lower.endsWith(".pdf")) LibraryFileType.PDF else null
    }

    private fun LibraryFileEntity.toDomain() = LibraryFile(
        id = id,
        title = title,
        type = LibraryFileType.valueOf(type),
        path = path,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        openedAt = openedAt,
        coverPath = coverPath,
        pageCount = pageCount,
    )

    private companion object {
        const val TAG = "LibraryRepository"
        const val COVER_MAX_DIMENSION = 720
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

fun File.existsSafe(): Boolean = exists() && isFile
