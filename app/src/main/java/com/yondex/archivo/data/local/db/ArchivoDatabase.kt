package com.yondex.archivo.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        DocumentFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ArchivoDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao
    abstract fun searchDao(): SearchDao

    companion object {
        const val NAME = "archivo.db"
    }
}
