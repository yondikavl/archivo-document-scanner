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

enum class PageFilter { ORIGINAL, ENHANCE, GRAYSCALE, BLACK_WHITE }

enum class OcrStatus { NONE, PENDING, RUNNING, DONE, FAILED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, ENGLISH, INDONESIAN }

enum class ViewMode { GRID, LIST }

enum class LibraryFileType { PDF, WORD }

enum class HomeFilter { ALL, SCANS, PDF }

data class LibraryFile(
    val id: Long,
    val title: String,
    val type: LibraryFileType,
    val path: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val openedAt: Long,
    val coverPath: String? = null,
    val pageCount: Int = 0,
)

sealed interface SearchResult {
    data class DocumentHit(val document: Document, val snippet: String?) : SearchResult
    data class PdfHit(val file: LibraryFile) : SearchResult
}

enum class PdfAnnotationType { HIGHLIGHT, COMMENT }

data class PdfAnnotation(
    val id: Long,
    val libraryFileId: Long,
    val pageIndex: Int,
    val type: PdfAnnotationType,
    /** Normalized page coords in 0..1. */
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Long,
    val text: String?,
    val createdAt: Long,
)

enum class SortOrder { UPDATED_DESC, CREATED_DESC, NAME_ASC, PAGES_DESC }

enum class PdfPageSize { A4, LETTER, FIT_IMAGE }

enum class ExportQuality(val maxDimension: Int, val jpegQuality: Int) {
    LOW(1200, 60),
    MEDIUM(1800, 75),
    HIGH(2600, 90),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultFilter: PageFilter = PageFilter.ENHANCE,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val exportQuality: ExportQuality = ExportQuality.MEDIUM,
    val autoOcr: Boolean = true,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
    val onboardingDone: Boolean = false,
)
