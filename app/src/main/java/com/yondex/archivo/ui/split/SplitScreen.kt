package com.yondex.archivo.ui.split

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.CallSplit
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.data.repository.LibraryRepository
import com.yondex.archivo.data.repository.SplitMode
import com.yondex.archivo.pdf.PdfRasterizer
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchSectionGap
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.navigation.NO_ID
import com.yondex.archivo.ui.navigation.SplitRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SplitPageItem(
    val key: String,
    val label: String,
    val pageId: Long? = null,
    val pdfIndex: Int? = null,
    val thumbPath: String? = null,
    val thumbBytes: ByteArray? = null,
)

data class SplitUiState(
    val title: String = "",
    val pages: List<SplitPageItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val mode: SplitMode = SplitMode.EXTRACT_ONE,
    val removeFromSource: Boolean = false,
    val isPdf: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val selectedCount: Int get() = selected.size
}

@HiltViewModel
class SplitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SplitRoute>()
    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()
    private val _done = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val done: SharedFlow<Long> = _done
    private val _askTitle = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val askTitle: SharedFlow<String> = _askTitle

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        when {
            route.documentId != NO_ID -> {
                val doc = documentRepository.getDocument(route.documentId)
                val pages = documentRepository.getPages(route.documentId).map { page ->
                    SplitPageItem(
                        key = "p_${page.id}",
                        label = "${page.index + 1}",
                        pageId = page.id,
                        thumbPath = page.processedPath,
                    )
                }
                _state.value = SplitUiState(
                    title = doc?.title.orEmpty(),
                    pages = pages,
                    selected = pages.map { it.key }.toSet(),
                    isPdf = false,
                    loading = false,
                    removeFromSource = false,
                )
            }
            route.libraryFileId != NO_ID -> {
                val file = libraryRepository.get(route.libraryFileId)
                if (file == null) {
                    _state.value = SplitUiState(loading = false, error = "PDF not found")
                    return
                }
                val pdf = File(file.path)
                val count = PdfRasterizer.pageCount(pdf)
                val pages = (0 until count).map { index ->
                    val bitmap = PdfRasterizer.renderPage(pdf, index, maxDimension = 400)
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
                    bitmap.recycle()
                    SplitPageItem(
                        key = "pdf_$index",
                        label = "${index + 1}",
                        pdfIndex = index,
                        thumbBytes = stream.toByteArray(),
                    )
                }
                _state.value = SplitUiState(
                    title = file.title,
                    pages = pages,
                    selected = pages.map { it.key }.toSet(),
                    isPdf = true,
                    loading = false,
                )
            }
            else -> _state.value = SplitUiState(loading = false, error = "Nothing to split")
        }
    }

    fun toggle(key: String) = _state.update {
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    fun selectAll() = _state.update { it.copy(selected = it.pages.map { p -> p.key }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun setMode(mode: SplitMode) = _state.update { it.copy(mode = mode) }

    fun setRemoveFromSource(value: Boolean) = _state.update { it.copy(removeFromSource = value) }

    fun requestSplit() {
        val state = _state.value
        if (state.selected.isEmpty()) return
        val default = when (state.mode) {
            SplitMode.EXTRACT_ONE -> state.title
            SplitMode.EACH_PAGE -> state.title
        }
        viewModelScope.launch { _askTitle.emit(default.ifBlank { "Split" }) }
    }

    fun confirmSplit(title: String) = viewModelScope.launch {
        val state = _state.value
        if (state.busy || state.selected.isEmpty()) return@launch
        if (!state.isPdf && state.removeFromSource && state.selected.size >= state.pages.size) {
            _state.update { it.copy(error = "Keep at least one page in the original") }
            return@launch
        }
        _state.update { it.copy(busy = true, error = null) }
        runCatching {
            val ordered = state.pages.filter { it.key in state.selected }
            when {
                route.documentId != NO_ID -> {
                    val ids = documentRepository.splitDocument(
                        documentId = route.documentId,
                        pageIds = ordered.mapNotNull { it.pageId },
                        mode = state.mode,
                        titlePrefix = title.trim().ifBlank { state.title },
                        removeFromSource = state.removeFromSource,
                    )
                    ids.first()
                }
                route.libraryFileId != NO_ID -> {
                    val file = libraryRepository.get(route.libraryFileId) ?: error("PDF missing")
                    val ids = documentRepository.createDocumentFromPdf(
                        libraryPath = file.path,
                        pageIndices = ordered.mapNotNull { it.pdfIndex },
                        title = title.trim().ifBlank { file.title },
                        folderId = null,
                        mode = state.mode,
                    )
                    ids.first()
                }
                else -> error("Nothing to split")
            }
        }.onSuccess { id ->
            _state.update { it.copy(busy = false) }
            _done.emit(id)
        }.onFailure { e ->
            _state.update { it.copy(busy = false, error = e.message ?: "Split failed") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    onBack: () -> Unit,
    onDone: (Long) -> Unit,
    viewModel: SplitViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var titleDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.done.collect { onDone(it) } }
    LaunchedEffect(Unit) { viewModel.askTitle.collect { titleDialog = it } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (state.selectedCount == state.pages.size) viewModel.clearSelection()
                        else viewModel.selectAll()
                    }) {
                        Icon(Icons.Sharp.Check, contentDescription = stringResource(R.string.action_select_all))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SketchSectionGap),
            ) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.split_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SketchItemGap))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == SplitMode.EXTRACT_ONE,
                        onClick = { viewModel.setMode(SplitMode.EXTRACT_ONE) },
                        label = { Text(stringResource(R.string.split_mode_one)) },
                        shape = RectangleShape,
                    )
                    FilterChip(
                        selected = state.mode == SplitMode.EACH_PAGE,
                        onClick = { viewModel.setMode(SplitMode.EACH_PAGE) },
                        label = { Text(stringResource(R.string.split_mode_each)) },
                        shape = RectangleShape,
                    )
                }
                if (!state.isPdf) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.split_remove_from_source),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = state.removeFromSource,
                            onCheckedChange = viewModel::setRemoveFromSource,
                        )
                    }
                }
                Spacer(Modifier.height(SketchItemGap))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(SketchItemGap),
                    verticalArrangement = Arrangement.spacedBy(SketchItemGap),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(state.pages, key = { it.key }) { page ->
                        val selected = page.key in state.selected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { viewModel.toggle(page.key) }
                                .border(
                                    if (selected) SketchStroke else SketchStroke,
                                    if (selected) MaterialTheme.colorScheme.primary else SketchInk,
                                    RectangleShape,
                                )
                                .padding(6.dp),
                        ) {
                            PageThumb(page, Modifier.fillMaxWidth().aspectRatio(0.72f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.page_cd, page.label.toIntOrNull() ?: 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                SketchFilledButton(
                    onClick = viewModel::requestSplit,
                    enabled = !state.busy && state.selectedCount > 0,
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Sharp.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.split_action_count, state.selectedCount))
                }
            }
        }
    }

    titleDialog?.let { initial ->
        TextInputDialog(
            title = stringResource(R.string.split_title_label),
            label = stringResource(R.string.document_title_label),
            initialValue = initial,
            confirmLabel = stringResource(R.string.split_action),
            onDismiss = { titleDialog = null },
            onConfirm = {
                titleDialog = null
                viewModel.confirmSplit(it)
            },
        )
    }
}

@Composable
private fun PageThumb(page: SplitPageItem, modifier: Modifier = Modifier) {
    val bitmap = remember(page.key, page.thumbPath, page.thumbBytes) {
        when {
            page.thumbBytes != null -> BitmapFactory.decodeByteArray(page.thumbBytes, 0, page.thumbBytes.size)
            page.thumbPath != null -> BitmapFactory.decodeFile(page.thumbPath)
            else -> null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(page.label)
        }
    }
}
