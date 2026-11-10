package com.yondex.archivo.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        DocumentFtsEntity::class,
        LibraryFileEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ArchivoDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao
    abstract fun searchDao(): SearchDao
    abstract fun libraryFileDao(): LibraryFileDao

    companion object {
        const val NAME = "archivo.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        path TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        opened_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_files_type ON library_files(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_files_opened_at ON library_files(opened_at)")
            }
        }
    }
}
