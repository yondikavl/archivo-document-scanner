package com.yondex.archivo.ui.navigation

import kotlinx.serialization.Serializable

const val NO_ID = -1L

@Serializable
data class HomeRoute(val folderId: Long = NO_ID)

@Serializable
data class ScannerRoute(val documentId: Long = NO_ID, val folderId: Long = NO_ID)

@Serializable
data object ReviewRoute

/** Edits either a page of the current scan session or a saved page. */
@Serializable
data class CropRoute(val sessionPageId: String? = null, val pageId: Long = NO_ID)

@Serializable
data class DocumentRoute(val documentId: Long)

@Serializable
data class PageViewerRoute(val documentId: Long, val initialIndex: Int = 0)

@Serializable
data object SearchRoute

@Serializable
data object TagsRoute

@Serializable
data object SettingsRoute

@Serializable
data object QrScannerRoute

@Serializable
data class PdfReaderRoute(val fileId: Long)

/** Comma-separated ids, e.g. "1,2,3". */
@Serializable
data class MergeRoute(val documentIds: String = "", val libraryFileIds: String = "")

@Serializable
data class SplitRoute(val documentId: Long = NO_ID, val libraryFileId: Long = NO_ID)
