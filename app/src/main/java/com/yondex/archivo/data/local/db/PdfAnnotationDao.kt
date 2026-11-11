package com.yondex.archivo.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations WHERE library_file_id = :libraryFileId ORDER BY page_index, created_at")
    fun observeForFile(libraryFileId: Long): Flow<List<PdfAnnotationEntity>>

    @Query("SELECT * FROM pdf_annotations WHERE id = :id")
    suspend fun get(id: Long): PdfAnnotationEntity?

    @Insert
    suspend fun insert(entity: PdfAnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfAnnotationEntity): Long

    @Query("UPDATE pdf_annotations SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pdf_annotations WHERE library_file_id = :libraryFileId")
    suspend fun deleteForFile(libraryFileId: Long)
}
