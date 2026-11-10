package com.yondex.archivo.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yondex.archivo.domain.model.OcrStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET parent_id = :parentId WHERE id = :id")
    suspend fun move(id: Long, parentId: Long?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}

private const val DOCUMENT_WITH_INFO = """
    SELECT d.*,
        (SELECT COUNT(*) FROM pages p WHERE p.document_id = d.id) AS page_count,
        (SELECT p.processed_path FROM pages p WHERE p.document_id = d.id
            ORDER BY p.page_index LIMIT 1) AS cover_path
    FROM documents d
"""

@Dao
interface DocumentDao {
    @Transaction
    @Query("$DOCUMENT_WITH_INFO ORDER BY d.updated_at DESC")
    fun observeAll(): Flow<List<DocumentWithInfo>>

    @Transaction
    @Query("$DOCUMENT_WITH_INFO WHERE d.id = :id")
    fun observe(id: Long): Flow<DocumentWithInfo?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Query("UPDATE documents SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE documents SET folder_id = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)

    @Query("UPDATE documents SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE document_id = :documentId ORDER BY page_index")
    fun observeByDocument(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE document_id = :documentId ORDER BY page_index")
    suspend fun getByDocument(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE document_id IN (:documentIds) ORDER BY document_id, page_index")
    suspend fun getByDocuments(documentIds: List<Long>): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun get(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE ocr_status = :status ORDER BY id LIMIT :limit")
    suspend fun getByStatus(status: OcrStatus, limit: Int): List<PageEntity>

    @Query("SELECT MAX(page_index) FROM pages WHERE document_id = :documentId")
    suspend fun maxIndex(documentId: Long): Int?

    @Insert
    suspend fun insertAll(pages: List<PageEntity>): List<Long>

    @Update
    suspend fun update(page: PageEntity)

    @Update
    suspend fun updateAll(pages: List<PageEntity>)

    @Query("UPDATE pages SET ocr_status = :status WHERE id = :id")
    suspend fun setOcrStatus(id: Long, status: OcrStatus)

    @Query("UPDATE pages SET ocr_status = :status WHERE document_id = :documentId")
    suspend fun setOcrStatusForDocument(documentId: Long, status: OcrStatus)

    @Query("UPDATE pages SET ocr_status = :to WHERE ocr_status = :from")
    suspend fun replaceOcrStatus(from: OcrStatus, to: OcrStatus)

    @Query("UPDATE pages SET ocr_text = :text, ocr_status = :status WHERE id = :id")
    suspend fun setOcrResult(id: Long, text: String?, status: OcrStatus)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT tag_id, COUNT(*) AS count FROM document_tags GROUP BY tag_id")
    fun observeUsage(): Flow<List<TagUsage>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT tag_id FROM document_tags WHERE document_id = :documentId")
    suspend fun tagIdsFor(documentId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<DocumentTagCrossRef>)

    @Query("DELETE FROM document_tags WHERE document_id = :documentId AND tag_id IN (:tagIds)")
    suspend fun deleteCrossRefs(documentId: Long, tagIds: List<Long>)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM document_fts WHERE rowid = :documentId")
    suspend fun delete(documentId: Long)

    @Insert
    suspend fun insert(entry: DocumentFtsEntity)

    @Query(
        """
        SELECT rowid AS document_id,
            snippet(document_fts, :highlightStart, :highlightEnd, '…', -1, 16) AS snippet
        FROM document_fts
        WHERE document_fts MATCH :query
        """
    )
    suspend fun search(query: String, highlightStart: String, highlightEnd: String): List<SearchHit>
}
