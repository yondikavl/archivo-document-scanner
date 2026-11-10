package com.yondex.archivo.domain.model

data class Folder(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val createdAt: Long,
)

data class Tag(
    val id: Long,
    val name: String,
    val color: Int,
)

data class Document(
    val id: Long,
    val title: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPath: String?,
    val tags: List<Tag>,
)

data class Page(
    val id: Long,
    val documentId: Long,
    val index: Int,
    val originalPath: String,
    val processedPath: String,
    val quad: Quad,
    val rotation: Int,
    val filter: PageFilter,
    val ocrText: String?,
    val ocrStatus: OcrStatus,
)

data class SearchResult(
    val document: Document,
    val snippet: String?,
)

enum class PageFilter { ORIGINAL, ENHANCE, GRAYSCALE, BLACK_WHITE }

enum class OcrStatus { NONE, PENDING, RUNNING, DONE, FAILED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { GRID, LIST }

enum class SortOrder { UPDATED_DESC, CREATED_DESC, NAME_ASC, PAGES_DESC }

enum class PdfPageSize { A4, LETTER, FIT_IMAGE }

enum class ExportQuality(val maxDimension: Int, val jpegQuality: Int) {
    LOW(1200, 60),
    MEDIUM(1800, 75),
    HIGH(2600, 90),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultFilter: PageFilter = PageFilter.ENHANCE,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val exportQuality: ExportQuality = ExportQuality.MEDIUM,
    val autoOcr: Boolean = true,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
)
