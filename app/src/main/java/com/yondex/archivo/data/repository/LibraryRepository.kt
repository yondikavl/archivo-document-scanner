package com.yondex.archivo.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.file.toSafeFileName
import com.yondex.archivo.data.local.db.LibraryFileDao
import com.yondex.archivo.data.local.db.LibraryFileEntity
import com.yondex.archivo.domain.model.LibraryFile
import com.yondex.archivo.domain.model.LibraryFileType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryFileDao,
    private val storage: FileStorage,
) {
    fun observeAll(): Flow<List<LibraryFile>> = dao.observeAll().map { list ->
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
        LibraryFile(id, safeName.substringBeforeLast('.').ifBlank { safeName }, type, target.absolutePath, target.length(), now, now)
    }

    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    suspend fun rename(id: Long, title: String) {
        val cleaned = title.trim()
        if (cleaned.isEmpty()) return
        dao.rename(id, cleaned)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.get(id) ?: return@withContext
        storage.deleteLibraryFile(entity.path)
        dao.delete(id)
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
    )
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

fun File.existsSafe(): Boolean = exists() && isFile
