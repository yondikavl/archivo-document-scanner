package com.yondex.archivo.data.local.db

import com.yondex.archivo.domain.model.Document
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.domain.model.Quad
import com.yondex.archivo.domain.model.Tag

fun FolderEntity.toDomain() = Folder(id = id, name = name, parentId = parentId, createdAt = createdAt)

fun TagEntity.toDomain() = Tag(id = id, name = name, color = color)

fun DocumentWithInfo.toDomain() = Document(
    id = document.id,
    title = document.title,
    folderId = document.folderId,
    createdAt = document.createdAt,
    updatedAt = document.updatedAt,
    pageCount = pageCount,
    coverPath = coverPath,
    tags = tags.map { it.toDomain() }.sortedBy { it.name.lowercase() },
)

fun PageEntity.toDomain() = Page(
    id = id,
    documentId = documentId,
    index = pageIndex,
    originalPath = originalPath,
    processedPath = processedPath,
    quad = Quad.decode(cropQuad),
    rotation = rotation,
    filter = filter,
    ocrText = ocrText,
    ocrStatus = ocrStatus,
)
