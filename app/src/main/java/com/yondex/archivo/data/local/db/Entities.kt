package com.yondex.archivo.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.domain.model.PageFilter

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parent_id")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folder_id")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "folder_id") val folderId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("document_id"), Index("ocr_status")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "processed_path") val processedPath: String,
    @ColumnInfo(name = "crop_quad") val cropQuad: String?,
    val rotation: Int,
    val filter: PageFilter,
    @ColumnInfo(name = "ocr_text") val ocrText: String?,
    @ColumnInfo(name = "ocr_status") val ocrStatus: OcrStatus,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)

@Entity(
    tableName = "document_tags",
    primaryKeys = ["document_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class DocumentTagCrossRef(
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/** Full-text index; rowid equals the document id. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "document_fts")
data class DocumentFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val content: String,
)

data class DocumentWithInfo(
    @Embedded val document: DocumentEntity,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DocumentTagCrossRef::class,
            parentColumn = "document_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

data class SearchHit(
    @ColumnInfo(name = "document_id") val documentId: Long,
    val snippet: String?,
)

data class TagUsage(
    @ColumnInfo(name = "tag_id") val tagId: Long,
    val count: Int,
)
