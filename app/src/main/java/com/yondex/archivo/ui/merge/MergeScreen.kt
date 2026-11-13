package com.yondex.archivo.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.yondex.archivo.data.repository.MergeSource
import com.yondex.archivo.ui.components.SketchCard
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchSectionGap
import com.yondex.archivo.ui.components.TextInputDialog
import com.yondex.archivo.ui.navigation.MergeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MergeItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val isPdf: Boolean,
    val documentId: Long? = null,
    val libraryFileId: Long? = null,
)

data class MergeUiState(
    val items: List<MergeItem> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MergeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MergeRoute>()
    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()
    private val _done = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val done: SharedFlow<Long> = _done
    private val _askTitle = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val askTitle: SharedFlow<String> = _askTitle

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val docIds = route.documentIds.split(',').mapNotNull { it.toLongOrNull() }.filter { it > 0 }
        val pdfIds = route.libraryFileIds.split(',').mapNotNull { it.toLongOrNull() }.filter { it > 0 }
        val items = buildList {
            docIds.forEach { id ->
                val doc = documentRepository.getDocument(id) ?: return@forEach
                add(
                    MergeItem(
                        key = "doc_$id",
                        title = doc.title,
                        subtitle = "${doc.pageCount} pg",
                        isPdf = false,
                        documentId = id,
                    ),
                )
            }
            pdfIds.forEach { id ->
                val file = libraryRepository.get(id) ?: return@forEach
                add(
                    MergeItem(
                        key = "pdf_$id",
                        title = file.title,
                        subtitle = "PDF",
                        isPdf = true,
                        libraryFileId = id,
                    ),
                )
            }
        }
        _state.value = MergeUiState(items = items)
    }

    fun move(index: Int, offset: Int) {
        _state.update { state ->
            val list = state.items.toMutableList()
            val target = index + offset
            if (index !in list.indices || target !in list.indices) return@update state
            list.add(target, list.removeAt(index))
            state.copy(items = list)
        }
    }

    fun requestMerge() {
        val defaultTitle = _state.value.items.joinToString(" + ") { it.title }.take(60)
        viewModelScope.launch { _askTitle.emit(defaultTitle.ifBlank { "Merged" }) }
    }

    fun confirmMerge(title: String) = viewModelScope.launch {
        val items = _state.value.items
        if (items.size < 2 || _state.value.busy) return@launch
        _state.update { it.copy(busy = true, error = null) }
        runCatching {
            val sources = items.map { item ->
                when {
                    item.documentId != null -> MergeSource.Document(item.documentId)
                    item.libraryFileId != null -> {
                        val path = libraryRepository.get(item.libraryFileId)?.path
                            ?: error("PDF missing")
                        MergeSource.Pdf(item.libraryFileId, path)
                    }
                    else -> error("Invalid merge item")
                }
            }
            documentRepository.mergeOrdered(
                sources = sources,
                title = title.trim().ifBlank { "Merged" },
                folderId = null,
            )
        }.onSuccess { id ->
            _state.update { it.copy(busy = false) }
            _done.emit(id)
        }.onFailure { e ->
            _state.update { it.copy(busy = false, error = e.message ?: "Merge failed") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    onBack: () -> Unit,
    onDone: (Long) -> Unit,
    viewModel: MergeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var titleDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.done.collect { onDone(it) } }
    LaunchedEffect(Unit) { viewModel.askTitle.collect { titleDialog = it } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = com.yondex.archivo.ui.theme.archivoTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SketchSectionGap),
        ) {
            Text(
                stringResource(R.string.merge_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SketchItemGap))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SketchItemGap),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
                    SketchCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_document),
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Sharp.KeyboardArrowUp, contentDescription = null)
                            }
                            IconButton(
                                onClick = { viewModel.move(index, 1) },
                                enabled = index < state.items.lastIndex,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_down),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            SketchFilledButton(
                onClick = viewModel::requestMerge,
                enabled = !state.busy && state.items.size >= 2,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_merge),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.merge_action))
            }
        }
    }

    titleDialog?.let { initial ->
        TextInputDialog(
            title = stringResource(R.string.merge_title_label),
            label = stringResource(R.string.document_title_label),
            initialValue = initial,
            confirmLabel = stringResource(R.string.merge_action),
            onDismiss = { titleDialog = null },
            onConfirm = {
                titleDialog = null
                viewModel.confirmMerge(it)
            },
        )
    }
}
