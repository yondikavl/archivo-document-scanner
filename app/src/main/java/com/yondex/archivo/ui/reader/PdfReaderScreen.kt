package com.yondex.archivo.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.Redo
import androidx.compose.material.icons.automirrored.sharp.Undo
import androidx.compose.material.icons.sharp.MoreVert
import androidx.annotation.DrawableRes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.LibraryRepository
import com.yondex.archivo.data.repository.PdfAnnotationRepository
import com.yondex.archivo.data.repository.formatFileSize
import com.yondex.archivo.domain.model.LibraryFile
import com.yondex.archivo.domain.model.PdfAnnotation
import com.yondex.archivo.domain.model.PdfAnnotationType
import com.yondex.archivo.pdf.PdfCompressor
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.SketchShape
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.SketchShapePill
import com.yondex.archivo.ui.components.SketchSheetDragHandle
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.navigation.PdfReaderRoute
import com.yondex.archivo.ui.pdf.CompressPdfSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class PdfTool { VIEW, HIGHLIGHT, COMMENT }

private sealed interface AnnotationEdit {
    data class Added(val annotation: PdfAnnotation) : AnnotationEdit
    data class Removed(val annotation: PdfAnnotation) : AnnotationEdit
    data class Updated(val before: PdfAnnotation, val after: PdfAnnotation) : AnnotationEdit
}

private sealed interface PdfDialog {
    data object Rename : PdfDialog
    data object DeleteFile : PdfDialog
    data object Compress : PdfDialog
    data class AddComment(val pageIndex: Int, val x: Float, val y: Float) : PdfDialog
    data class EditComment(val annotation: PdfAnnotation) : PdfDialog
    data class DeleteAnnotation(val annotation: PdfAnnotation) : PdfDialog
}

sealed interface CompressEvent {
    data class Success(val bytes: Long, val targetBytes: Long, val metTarget: Boolean) : CompressEvent
    data class Failed(val message: String) : CompressEvent
    data object AlreadySmall : CompressEvent
}

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val annotationRepository: PdfAnnotationRepository,
) : ViewModel() {
    private val fileId = savedStateHandle.toRoute<PdfReaderRoute>().fileId
    private val _file = MutableStateFlow<LibraryFile?>(null)
    val file: StateFlow<LibraryFile?> = _file.asStateFlow()
    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()
    /** Bumped when the underlying PDF file changes (e.g. compress) so pages reload. */
    private val _documentEpoch = MutableStateFlow(0)
    val documentEpoch: StateFlow<Int> = _documentEpoch.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val undoStack = ArrayDeque<AnnotationEdit>()
    private val redoStack = ArrayDeque<AnnotationEdit>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    private val _compressing = MutableStateFlow(false)
    val compressing: StateFlow<Boolean> = _compressing.asStateFlow()
    private val _compressEvents = MutableSharedFlow<CompressEvent>(extraBufferCapacity = 1)
    val compressEvents: SharedFlow<CompressEvent> = _compressEvents
    private val pdfMutex = Mutex()
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    /** Single-thread dispatcher — PdfRenderer is not thread-safe. */
    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    val annotations: StateFlow<List<PdfAnnotation>> = annotationRepository.observeForFile(fileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val item = libraryRepository.get(fileId)
            _file.value = item
            if (item != null) {
                libraryRepository.touch(fileId)
                _pageCount.value = openPdf(File(item.path))
            }
            _loading.value = false
        }
    }

    /** Renders one page on demand. Caller owns the returned bitmap (must recycle). */
    suspend fun renderPage(index: Int): Bitmap? {
        if (index !in 0 until _pageCount.value) return null
        return withContext(pdfDispatcher) {
            pdfMutex.withLock {
                runCatching { renderPageLocked(index) }.getOrNull()
            }
        }
    }

    fun rename(title: String) = viewModelScope.launch {
        libraryRepository.rename(fileId, title)
        _file.value = libraryRepository.get(fileId)
    }

    fun delete() = viewModelScope.launch {
        libraryRepository.delete(fileId)
        _deleted.value = true
    }

    fun compress(maxBytes: Long = PdfCompressor.TARGET_100KB) = viewModelScope.launch {
        if (_compressing.value) return@launch
        val current = _file.value ?: return@launch
        if (current.sizeBytes in 1..maxBytes) {
            _compressEvents.emit(CompressEvent.AlreadySmall)
            return@launch
        }
        _compressing.value = true
        runCatching {
            libraryRepository.compressPdf(fileId, maxBytes)
        }.onSuccess { (updated, result) ->
            _file.value = updated
            _pageCount.value = openPdf(File(updated.path))
            _documentEpoch.value = _documentEpoch.value + 1
            _compressEvents.emit(
                CompressEvent.Success(
                    bytes = result.bytes,
                    targetBytes = maxBytes,
                    metTarget = result.metTarget,
                ),
            )
        }.onFailure { e ->
            _compressEvents.emit(CompressEvent.Failed(e.message ?: "unknown"))
        }
        _compressing.value = false
    }

    fun addHighlight(pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float) =
        viewModelScope.launch {
            val id = annotationRepository.addHighlight(fileId, pageIndex, left, top, right, bottom)
            if (id > 0) {
                annotationRepository.get(id)?.let { pushUndo(AnnotationEdit.Added(it)) }
            }
        }

    fun addComment(pageIndex: Int, text: String, x: Float, y: Float) = viewModelScope.launch {
        val id = annotationRepository.addComment(fileId, pageIndex, text, x, y)
        if (id > 0) {
            annotationRepository.get(id)?.let { pushUndo(AnnotationEdit.Added(it)) }
        }
    }

    fun updateComment(id: Long, text: String) = viewModelScope.launch {
        val before = annotationRepository.get(id) ?: return@launch
        annotationRepository.updateComment(id, text)
        val after = annotationRepository.get(id) ?: return@launch
        if (before.text != after.text) {
            pushUndo(AnnotationEdit.Updated(before, after))
        }
    }

    fun deleteAnnotation(id: Long) = viewModelScope.launch {
        val existing = annotationRepository.get(id) ?: return@launch
        annotationRepository.delete(id)
        pushUndo(AnnotationEdit.Removed(existing))
    }

    fun undo() = viewModelScope.launch {
        val edit = undoStack.removeLastOrNull() ?: return@launch
        when (edit) {
            is AnnotationEdit.Added -> annotationRepository.delete(edit.annotation.id)
            is AnnotationEdit.Removed -> annotationRepository.restore(edit.annotation)
            is AnnotationEdit.Updated -> annotationRepository.updateComment(
                edit.before.id,
                edit.before.text.orEmpty(),
            )
        }
        redoStack.addLast(edit)
        publishHistory()
    }

    fun redo() = viewModelScope.launch {
        val edit = redoStack.removeLastOrNull() ?: return@launch
        when (edit) {
            is AnnotationEdit.Added -> annotationRepository.restore(edit.annotation)
            is AnnotationEdit.Removed -> annotationRepository.delete(edit.annotation.id)
            is AnnotationEdit.Updated -> annotationRepository.updateComment(
                edit.after.id,
                edit.after.text.orEmpty(),
            )
        }
        undoStack.addLast(edit)
        publishHistory()
    }

    private fun pushUndo(edit: AnnotationEdit) {
        undoStack.addLast(edit)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        publishHistory()
    }

    private fun publishHistory() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    override fun onCleared() {
        viewModelScope.launch(pdfDispatcher) {
            pdfMutex.withLock { closePdfLocked() }
        }
        super.onCleared()
    }

    private suspend fun openPdf(file: File): Int = withContext(pdfDispatcher) {
        pdfMutex.withLock {
            closePdfLocked()
            if (!file.exists()) return@withLock 0
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            pdfDescriptor = descriptor
            pdfRenderer = renderer
            renderer.pageCount
        }
    }

    private fun closePdfLocked() {
        runCatching { pdfRenderer?.close() }
        runCatching { pdfDescriptor?.close() }
        pdfRenderer = null
        pdfDescriptor = null
    }

    private fun renderPageLocked(index: Int): Bitmap {
        val renderer = pdfRenderer ?: error("PDF not open")
        val page = renderer.openPage(index)
        try {
            val longest = max(page.width, page.height).coerceAtLeast(1)
            val scale = min(2f, PAGE_MAX_DIMENSION.toFloat() / longest)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            AndroidCanvas(bitmap).drawColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    companion object {
        private const val MAX_HISTORY = 40
        private const val PAGE_MAX_DIMENSION = 1600
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    onBack: () -> Unit,
    onSplit: (fileId: Long) -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel(),
) {
    val file by viewModel.file.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val documentEpoch by viewModel.documentEpoch.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val compressing by viewModel.compressing.collectAsStateWithLifecycle()
    var tool by remember { mutableStateOf(PdfTool.VIEW) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PdfDialog?>(null) }
    val context = LocalContext.current

    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(Unit) {
        viewModel.compressEvents.collect { event ->
            val message = when (event) {
                is CompressEvent.Success -> if (event.metTarget) {
                    context.getString(R.string.compress_success, formatFileSize(event.bytes))
                } else {
                    context.getString(
                        R.string.compress_success_near,
                        formatFileSize(event.bytes),
                        formatFileSize(event.targetBytes),
                    )
                }
                is CompressEvent.Failed -> context.getString(R.string.compress_failed, event.message)
                CompressEvent.AlreadySmall -> context.getString(R.string.compress_already_small)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (event is CompressEvent.Success || event is CompressEvent.AlreadySmall) {
                dialog = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        topBar = {
            TopAppBar(
                title = {
                    Text(file?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = canUndo) {
                        Icon(
                            Icons.AutoMirrored.Sharp.Undo,
                            contentDescription = stringResource(R.string.action_undo),
                        )
                    }
                    IconButton(onClick = viewModel::redo, enabled = canRedo) {
                        Icon(
                            Icons.AutoMirrored.Sharp.Redo,
                            contentDescription = stringResource(R.string.action_redo),
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Sharp.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (!loading && pageCount > 0) {
                PdfToolBar(
                    selected = tool,
                    onSelect = { tool = it },
                    hint = when (tool) {
                        PdfTool.HIGHLIGHT -> stringResource(R.string.pdf_highlight_hint)
                        PdfTool.COMMENT -> stringResource(R.string.pdf_comment_hint)
                        PdfTool.VIEW -> null
                    },
                )
            }
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            pageCount <= 0 -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reader_open_failed))
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(pageCount, key = { "$documentEpoch-$it" }) { index ->
                    PdfPageItem(
                        index = index,
                        documentEpoch = documentEpoch,
                        annotations = annotations.filter { it.pageIndex == index },
                        tool = tool,
                        renderPage = viewModel::renderPage,
                        onHighlight = { left, top, right, bottom ->
                            viewModel.addHighlight(index, left, top, right, bottom)
                        },
                        onCommentTap = { x, y ->
                            dialog = PdfDialog.AddComment(index, x, y)
                        },
                        onAnnotationClick = { annotation ->
                            dialog = when (annotation.type) {
                                PdfAnnotationType.COMMENT -> PdfDialog.EditComment(annotation)
                                PdfAnnotationType.HIGHLIGHT -> PdfDialog.DeleteAnnotation(annotation)
                            }
                        },
                        onAnnotationLongClick = { annotation ->
                            dialog = PdfDialog.DeleteAnnotation(annotation)
                        },
                    )
                }
            }
        }
    }

    if (menuOpen) {
        PdfActionsSheet(
            canCompress = file != null && pageCount > 0 && !compressing,
            canSplit = file != null && pageCount > 0,
            onDismiss = { menuOpen = false },
            onCompress = {
                menuOpen = false
                dialog = PdfDialog.Compress
            },
            onSplit = {
                menuOpen = false
                file?.id?.let(onSplit)
            },
            onRename = {
                menuOpen = false
                dialog = PdfDialog.Rename
            },
            onDelete = {
                menuOpen = false
                dialog = PdfDialog.DeleteFile
            },
        )
    }

    when (val current = dialog) {
        PdfDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.rename_library_file),
            label = stringResource(R.string.file_name),
            initialValue = file?.title.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.rename(it); dialog = null },
        )
        PdfDialog.DeleteFile -> ConfirmDialog(
            title = stringResource(R.string.delete_library_file_title),
            message = stringResource(R.string.delete_library_file_message, file?.title.orEmpty()),
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.delete() },
        )
        PdfDialog.Compress -> CompressPdfSheet(
            currentBytes = file?.sizeBytes ?: 0L,
            compressing = compressing,
            onDismiss = { if (!compressing) dialog = null },
            onCompress = { targetBytes -> viewModel.compress(targetBytes) },
        )
        is PdfDialog.AddComment -> TextInputDialog(
            title = stringResource(R.string.pdf_add_comment),
            label = stringResource(R.string.pdf_comment_label),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.addComment(current.pageIndex, it, current.x, current.y)
                dialog = null
            },
        )
        is PdfDialog.EditComment -> TextInputDialog(
            title = stringResource(R.string.pdf_edit_comment),
            label = stringResource(R.string.pdf_comment_label),
            initialValue = current.annotation.text.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.updateComment(current.annotation.id, it)
                dialog = null
            },
        )
        is PdfDialog.DeleteAnnotation -> ConfirmDialog(
            title = stringResource(R.string.pdf_delete_annotation),
            message = stringResource(R.string.pdf_delete_annotation_message),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.deleteAnnotation(current.annotation.id)
                dialog = null
            },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfActionsSheet(
    canCompress: Boolean,
    canSplit: Boolean,
    onDismiss: () -> Unit,
    onCompress: () -> Unit,
    onSplit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SketchShapeLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SketchSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.action_more),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ActionMenuRow(
                title = stringResource(R.string.compress_action),
                iconRes = R.drawable.ic_compress,
                enabled = canCompress,
                onClick = onCompress,
            )
            ActionMenuRow(
                title = stringResource(R.string.split_action),
                iconRes = R.drawable.ic_split,
                enabled = canSplit,
                onClick = onSplit,
            )
            ActionMenuRow(
                title = stringResource(R.string.action_rename),
                iconRes = R.drawable.ic_rename,
                onClick = onRename,
            )
            ActionMenuRow(
                title = stringResource(R.string.action_delete),
                iconRes = R.drawable.ic_delete,
                onClick = onDelete,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionMenuRow(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SketchShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(SketchShape)
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    documentEpoch: Int,
    annotations: List<PdfAnnotation>,
    tool: PdfTool,
    renderPage: suspend (Int) -> Bitmap?,
    onHighlight: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onCommentTap: (x: Float, y: Float) -> Unit,
    onAnnotationClick: (PdfAnnotation) -> Unit,
    onAnnotationLongClick: (PdfAnnotation) -> Unit,
) {
    var bitmap by remember(index, documentEpoch) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(index, documentEpoch) { mutableStateOf(false) }
    val held = remember(index, documentEpoch) { arrayOfNulls<Bitmap>(1) }
    var retryToken by remember(index, documentEpoch) { mutableStateOf(0) }

    LaunchedEffect(index, documentEpoch, retryToken) {
        failed = false
        val rendered = renderPage(index)
        held[0]?.takeIf { !it.isRecycled && it !== rendered }?.recycle()
        held[0] = rendered
        bitmap = rendered
        failed = rendered == null
    }
    DisposableEffect(index, documentEpoch) {
        onDispose {
            held[0]?.takeIf { !it.isRecycled }?.recycle()
            held[0] = null
        }
    }

    val current = bitmap
    when {
        current != null && !current.isRecycled -> AnnotatedPdfPage(
            bitmap = current,
            pageIndex = index,
            annotations = annotations,
            tool = tool,
            onHighlight = onHighlight,
            onCommentTap = onCommentTap,
            onAnnotationClick = onAnnotationClick,
            onAnnotationLongClick = onAnnotationLongClick,
        )
        failed -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            TextButton(onClick = { retryToken++ }) {
                Text(stringResource(R.string.action_retry))
            }
        }
        else -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PdfToolBar(
    selected: PdfTool,
    onSelect: (PdfTool) -> Unit,
    hint: String?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PdfToolChip(
                    label = stringResource(R.string.pdf_tool_view),
                    selected = selected == PdfTool.VIEW,
                    onClick = { onSelect(PdfTool.VIEW) },
                    modifier = Modifier.weight(1f),
                )
                PdfToolChip(
                    label = stringResource(R.string.pdf_tool_highlight),
                    selected = selected == PdfTool.HIGHLIGHT,
                    onClick = { onSelect(PdfTool.HIGHLIGHT) },
                    modifier = Modifier.weight(1f),
                )
                PdfToolChip(
                    label = stringResource(R.string.pdf_tool_comment),
                    selected = selected == PdfTool.COMMENT,
                    onClick = { onSelect(PdfTool.COMMENT) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                hint ?: stringResource(R.string.pdf_annotations_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PdfToolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = SketchShapePill,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AnnotatedPdfPage(
    bitmap: Bitmap,
    pageIndex: Int,
    annotations: List<PdfAnnotation>,
    tool: PdfTool,
    onHighlight: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onCommentTap: (x: Float, y: Float) -> Unit,
    onAnnotationClick: (PdfAnnotation) -> Unit,
    onAnnotationLongClick: (PdfAnnotation) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    fun hitTest(offset: Offset): PdfAnnotation? {
        if (size.width == 0 || size.height == 0) return null
        val nx = offset.x / size.width
        val ny = offset.y / size.height
        return annotations.lastOrNull { ann ->
            nx in ann.left..ann.right && ny in ann.top..ann.bottom
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { size = it },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.page_cd, pageIndex + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(tool, size, annotations) {
                    when (tool) {
                        PdfTool.HIGHLIGHT -> detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragCurrent = offset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragCurrent = change.position
                            },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragCurrent
                                dragStart = null
                                dragCurrent = null
                                if (start != null && end != null && size.width > 0 && size.height > 0) {
                                    val left = min(start.x, end.x) / size.width
                                    val top = min(start.y, end.y) / size.height
                                    val right = max(start.x, end.x) / size.width
                                    val bottom = max(start.y, end.y) / size.height
                                    if (abs(right - left) >= 0.01f && abs(bottom - top) >= 0.01f) {
                                        onHighlight(left, top, right, bottom)
                                    }
                                }
                            },
                            onDragCancel = {
                                dragStart = null
                                dragCurrent = null
                            },
                        )
                        PdfTool.COMMENT -> detectTapGestures { offset ->
                            if (size.width > 0 && size.height > 0) {
                                onCommentTap(offset.x / size.width, offset.y / size.height)
                            }
                        }
                        PdfTool.VIEW -> detectTapGestures(
                            onLongPress = { offset -> hitTest(offset)?.let(onAnnotationLongClick) },
                            onTap = { offset -> hitTest(offset)?.let(onAnnotationClick) },
                        )
                    }
                },
        ) {
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            annotations.forEach { ann ->
                val color = Color(ann.color)
                val left = ann.left * w
                val top = ann.top * h
                val width = (ann.right - ann.left) * w
                val height = (ann.bottom - ann.top) * h
                when (ann.type) {
                    PdfAnnotationType.HIGHLIGHT -> drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                    )
                    PdfAnnotationType.COMMENT -> {
                        drawCircle(
                            color = color,
                            radius = max(width, height) / 2f,
                            center = Offset(left + width / 2f, top + height / 2f),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = max(width, height) / 5f,
                            center = Offset(left + width / 2f, top + height / 2f),
                        )
                    }
                }
            }
            val start = dragStart
            val current = dragCurrent
            if (start != null && current != null) {
                val left = min(start.x, current.x)
                val top = min(start.y, current.y)
                val width = abs(current.x - start.x)
                val height = abs(current.y - start.y)
                drawRect(
                    color = Color(PdfAnnotationRepository.DEFAULT_HIGHLIGHT_COLOR),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                )
                drawRect(
                    color = Color(0xFF0F9D58),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
