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
        PdfAnnotationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ArchivoDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao
    abstract fun searchDao(): SearchDao
    abstract fun libraryFileDao(): LibraryFileDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_annotations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        library_file_id INTEGER NOT NULL,
                        page_index INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        left REAL NOT NULL,
                        top REAL NOT NULL,
                        right REAL NOT NULL,
                        bottom REAL NOT NULL,
                        color INTEGER NOT NULL,
                        text TEXT,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(library_file_id) REFERENCES library_files(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_annotations_library_file_id ON pdf_annotations(library_file_id)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pdf_annotations_library_file_id_page_index ON pdf_annotations(library_file_id, page_index)",
                )
            }
        }
    }
}
