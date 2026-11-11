package com.yondex.archivo.data.repository

import com.yondex.archivo.data.local.db.PdfAnnotationDao
import com.yondex.archivo.data.local.db.PdfAnnotationEntity
import com.yondex.archivo.domain.model.PdfAnnotation
import com.yondex.archivo.domain.model.PdfAnnotationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfAnnotationRepository @Inject constructor(
    private val dao: PdfAnnotationDao,
) {
    fun observeForFile(libraryFileId: Long): Flow<List<PdfAnnotation>> =
        dao.observeForFile(libraryFileId).map { list -> list.map { it.toDomain() } }

    suspend fun addHighlight(
        libraryFileId: Long,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long = DEFAULT_HIGHLIGHT_COLOR,
    ): Long {
        val l = left.coerceIn(0f, 1f)
        val t = top.coerceIn(0f, 1f)
        val r = right.coerceIn(0f, 1f)
        val b = bottom.coerceIn(0f, 1f)
        if (kotlin.math.abs(r - l) < MIN_SIZE || kotlin.math.abs(b - t) < MIN_SIZE) return -1L
        return dao.insert(
            PdfAnnotationEntity(
                libraryFileId = libraryFileId,
                pageIndex = pageIndex,
                type = PdfAnnotationType.HIGHLIGHT.name,
                left = minOf(l, r),
                top = minOf(t, b),
                right = maxOf(l, r),
                bottom = maxOf(t, b),
                color = color,
                text = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun addComment(
        libraryFileId: Long,
        pageIndex: Int,
        text: String,
        x: Float,
        y: Float,
        color: Long = DEFAULT_COMMENT_COLOR,
    ): Long {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return -1L
        val cx = x.coerceIn(0f, 1f)
        val cy = y.coerceIn(0f, 1f)
        val half = COMMENT_MARKER / 2f
        return dao.insert(
            PdfAnnotationEntity(
                libraryFileId = libraryFileId,
                pageIndex = pageIndex,
                type = PdfAnnotationType.COMMENT.name,
                left = (cx - half).coerceIn(0f, 1f),
                top = (cy - half).coerceIn(0f, 1f),
                right = (cx + half).coerceIn(0f, 1f),
                bottom = (cy + half).coerceIn(0f, 1f),
                color = color,
                text = cleaned,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun get(id: Long): PdfAnnotation? = dao.get(id)?.toDomain()

    suspend fun updateComment(id: Long, text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        dao.updateText(id, cleaned)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun restore(annotation: PdfAnnotation): Long = dao.upsert(
        PdfAnnotationEntity(
            id = annotation.id,
            libraryFileId = annotation.libraryFileId,
            pageIndex = annotation.pageIndex,
            type = annotation.type.name,
            left = annotation.left,
            top = annotation.top,
            right = annotation.right,
            bottom = annotation.bottom,
            color = annotation.color,
            text = annotation.text,
            createdAt = annotation.createdAt,
        ),
    )

    private fun PdfAnnotationEntity.toDomain() = PdfAnnotation(
        id = id,
        libraryFileId = libraryFileId,
        pageIndex = pageIndex,
        type = PdfAnnotationType.valueOf(type),
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        color = color,
        text = text,
        createdAt = createdAt,
    )

    companion object {
        const val DEFAULT_HIGHLIGHT_COLOR = 0x66FFD60AL
        const val DEFAULT_COMMENT_COLOR = 0xFF0F9D58L
        private const val MIN_SIZE = 0.01f
        private const val COMMENT_MARKER = 0.04f
    }
}
