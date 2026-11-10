package com.yondex.archivo.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryFileDao {
    @Query("SELECT * FROM library_files ORDER BY opened_at DESC")
    fun observeAll(): Flow<List<LibraryFileEntity>>

    @Query("SELECT * FROM library_files WHERE type = :type ORDER BY opened_at DESC")
    fun observeByType(type: String): Flow<List<LibraryFileEntity>>

    @Query("SELECT * FROM library_files WHERE id = :id")
    suspend fun get(id: Long): LibraryFileEntity?

    @Insert
    suspend fun insert(file: LibraryFileEntity): Long

    @Query("UPDATE library_files SET opened_at = :openedAt WHERE id = :id")
    suspend fun touch(id: Long, openedAt: Long)

    @Query("DELETE FROM library_files WHERE id = :id")
    suspend fun delete(id: Long)
}
