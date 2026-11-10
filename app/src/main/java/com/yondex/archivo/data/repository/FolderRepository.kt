package com.yondex.archivo.data.repository

import com.yondex.archivo.data.local.db.FolderDao
import com.yondex.archivo.data.local.db.FolderEntity
import com.yondex.archivo.data.local.db.toDomain
import com.yondex.archivo.domain.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao: FolderDao,
) {
    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun create(name: String, parentId: Long?): Long =
        folderDao.insert(FolderEntity(name = name, parentId = parentId, createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = folderDao.rename(id, name)

    /** Deletes the folder and its subfolders; contained documents move to the root. */
    suspend fun delete(id: Long) = folderDao.delete(id)
}

/** Flattens folders into a depth-first list with their nesting depth, for pickers. */
fun List<Folder>.asTree(): List<Pair<Folder, Int>> {
    val children = groupBy { it.parentId }
    val result = mutableListOf<Pair<Folder, Int>>()
    fun visit(parentId: Long?, depth: Int) {
        children[parentId].orEmpty().forEach { folder ->
            result += folder to depth
            visit(folder.id, depth + 1)
        }
    }
    visit(null, 0)
    return result
}

/** Returns the chain of folders from the root down to [folderId]. */
fun List<Folder>.pathTo(folderId: Long?): List<Folder> {
    val byId = associateBy { it.id }
    val path = mutableListOf<Folder>()
    var current = folderId?.let(byId::get)
    while (current != null && path.size < size) {
        path.add(0, current)
        current = current.parentId?.let(byId::get)
    }
    return path
}
