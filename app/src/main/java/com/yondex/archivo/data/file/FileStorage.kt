package com.yondex.archivo.data.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sessionDir: File
        get() = File(context.filesDir, "scan_session").apply { mkdirs() }

    val exportDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    fun newSessionFile(prefix: String): File = File(sessionDir, "${prefix}_${UUID.randomUUID()}.jpg")

    fun clearSession() {
        sessionDir.deleteRecursively()
    }

    fun documentDir(documentId: Long): File =
        File(context.filesDir, "documents/$documentId").apply { mkdirs() }

    fun libraryDir(): File =
        File(context.filesDir, "library").apply { mkdirs() }

    fun newLibraryFile(name: String): File =
        File(libraryDir(), "${UUID.randomUUID()}_$name")

    fun libraryCoverFile(libraryFileId: Long): File =
        File(libraryDir(), "covers/$libraryFileId.jpg").also { it.parentFile?.mkdirs() }

    fun deleteLibraryFile(path: String) {
        File(path).delete()
    }

    fun deleteLibraryCover(libraryFileId: Long) {
        libraryCoverFile(libraryFileId).delete()
    }

    fun newPageFile(documentId: Long, prefix: String): File =
        File(documentDir(documentId), "${prefix}_${UUID.randomUUID()}.jpg")

    fun deleteDocument(documentId: Long) {
        File(context.filesDir, "documents/$documentId").deleteRecursively()
    }

    fun clearExports() {
        exportDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Moves [source] to [target], falling back to copy when a rename is not possible. */
    fun move(source: File, target: File): File {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return target
    }

    fun copy(source: File, target: File): File {
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        return target
    }

    fun delete(path: String?) {
        if (!path.isNullOrEmpty()) File(path).delete()
    }
}

fun String.toSafeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "document" }.take(80)
