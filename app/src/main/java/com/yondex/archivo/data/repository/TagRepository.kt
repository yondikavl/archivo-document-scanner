package com.yondex.archivo.data.repository

import androidx.room.withTransaction
import com.yondex.archivo.data.local.db.DocumentTagCrossRef
import com.yondex.archivo.data.local.db.ArchivoDatabase
import com.yondex.archivo.data.local.db.TagDao
import com.yondex.archivo.data.local.db.TagEntity
import com.yondex.archivo.data.local.db.toDomain
import com.yondex.archivo.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val database: ArchivoDatabase,
    private val tagDao: TagDao,
) {
    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeUsage(): Flow<Map<Long, Int>> =
        tagDao.observeUsage().map { list -> list.associate { it.tagId to it.count } }

    /** Creates a tag, or returns the existing one with the same name. */
    suspend fun create(name: String, color: Int): Long {
        val trimmed = name.trim()
        tagDao.findByName(trimmed)?.let { return it.id }
        return tagDao.insert(TagEntity(name = trimmed, color = color))
    }

    suspend fun update(tag: Tag) = tagDao.update(TagEntity(id = tag.id, name = tag.name.trim(), color = tag.color))

    suspend fun delete(id: Long) = tagDao.delete(id)

    suspend fun setTags(documentId: Long, tagIds: Set<Long>) = database.withTransaction {
        val current = tagDao.tagIdsFor(documentId).toSet()
        val removed = current - tagIds
        if (removed.isNotEmpty()) tagDao.deleteCrossRefs(documentId, removed.toList())
        tagDao.insertCrossRefs((tagIds - current).map { DocumentTagCrossRef(documentId, it) })
    }

    /**
     * Applies a tag edit to several documents: tags in [added] are attached to all, tags in
     * [removed] are detached from all, and other tags are left untouched.
     */
    suspend fun editTags(documentIds: List<Long>, added: Set<Long>, removed: Set<Long>) =
        database.withTransaction {
            documentIds.forEach { documentId ->
                if (removed.isNotEmpty()) tagDao.deleteCrossRefs(documentId, removed.toList())
                tagDao.insertCrossRefs(added.map { DocumentTagCrossRef(documentId, it) })
            }
        }

    companion object {
        val PALETTE = listOf(
            0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
            0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(),
            0xFFFDD835.toInt(), 0xFFFB8C00.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt(),
        )
    }
}
