package com.yondex.archivo.di

import android.content.Context
import androidx.room.Room
import com.yondex.archivo.data.local.db.ArchivoDatabase
import com.yondex.archivo.data.local.db.DocumentDao
import com.yondex.archivo.data.local.db.FolderDao
import com.yondex.archivo.data.local.db.LibraryFileDao
import com.yondex.archivo.data.local.db.PageDao
import com.yondex.archivo.data.local.db.SearchDao
import com.yondex.archivo.data.local.db.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArchivoDatabase =
        Room.databaseBuilder(context, ArchivoDatabase::class.java, ArchivoDatabase.NAME)
            .addMigrations(ArchivoDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideFolderDao(db: ArchivoDatabase): FolderDao = db.folderDao()
    @Provides fun provideDocumentDao(db: ArchivoDatabase): DocumentDao = db.documentDao()
    @Provides fun providePageDao(db: ArchivoDatabase): PageDao = db.pageDao()
    @Provides fun provideTagDao(db: ArchivoDatabase): TagDao = db.tagDao()
    @Provides fun provideSearchDao(db: ArchivoDatabase): SearchDao = db.searchDao()
    @Provides fun provideLibraryFileDao(db: ArchivoDatabase): LibraryFileDao = db.libraryFileDao()
}
