package com.yondex.archivo.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.data.repository.TagRepository
import com.yondex.archivo.domain.model.Tag
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.TagDot
import com.yondex.archivo.ui.components.TagEditorDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TagsViewModel @Inject constructor(private val tagRepository: TagRepository) : ViewModel() {
    val tags: StateFlow<List<Pair<Tag, Int>>> = combine(
        tagRepository.observeTags(),
        tagRepository.observeUsage(),
    ) { tags, usage -> tags.map { it to (usage[it.id] ?: 0) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, color: Int) = viewModelScope.launch { tagRepository.create(name, color) }

    fun update(tag: Tag) = viewModelScope.launch { runCatching { tagRepository.update(tag) } }

    fun delete(tag: Tag) = viewModelScope.launch { tagRepository.delete(tag.id) }
}

private sealed interface TagDialog {
    data object Create : TagDialog
    data class Edit(val tag: Tag) : TagDialog
    data class Delete(val tag: Tag) : TagDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onBack: () -> Unit, viewModel: TagsViewModel = hiltViewModel()) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<TagDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola tag") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = TagDialog.Create }) {
                Icon(Icons.Default.Add, contentDescription = "Tag baru")
            }
        },
    ) { padding ->
        if (tags.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.Label,
                title = "Belum ada tag",
                message = "Tag membantu mengelompokkan dokumen lintas folder.",
                modifier = Modifier.padding(padding).padding(top = 64.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            items(tags, key = { it.first.id }) { (tag, count) ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    supportingContent = { Text("$count dokumen") },
                    leadingContent = { TagDot(Color(tag.color), Modifier.size(16.dp)) },
                    trailingContent = {
                        IconButton(onClick = { dialog = TagDialog.Delete(tag) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus")
                        }
                    },
                    modifier = Modifier.clickable { dialog = TagDialog.Edit(tag) },
                )
            }
        }
    }

    when (val current = dialog) {
        TagDialog.Create -> TagEditorDialog(
            title = "Tag baru",
            initialColor = TagRepository.PALETTE[tags.size % TagRepository.PALETTE.size],
            onDismiss = { dialog = null },
            onConfirm = { name, color -> viewModel.create(name, color); dialog = null },
        )
        is TagDialog.Edit -> TagEditorDialog(
            title = "Edit tag",
            initialName = current.tag.name,
            initialColor = current.tag.color,
            onDismiss = { dialog = null },
            onConfirm = { name, color -> viewModel.update(current.tag.copy(name = name, color = color)); dialog = null },
        )
        is TagDialog.Delete -> ConfirmDialog(
            title = "Hapus tag?",
            message = "Tag \"${current.tag.name}\" akan dilepas dari semua dokumen.",
            onDismiss = { dialog = null },
            onConfirm = { viewModel.delete(current.tag); dialog = null },
        )
        null -> Unit
    }
}
