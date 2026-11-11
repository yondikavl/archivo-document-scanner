package com.yondex.archivo.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.TagRepository
import com.yondex.archivo.domain.model.Tag
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.SketchCard
import com.yondex.archivo.ui.components.SketchFab
import com.yondex.archivo.ui.components.SketchItemGap
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_tags)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            SketchFab(onClick = { dialog = TagDialog.Create }) {
                Icon(Icons.Sharp.Add, contentDescription = stringResource(R.string.new_tag))
            }
        },
    ) { padding ->
        if (tags.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_tag,
                title = stringResource(R.string.empty_tags_title),
                message = stringResource(R.string.empty_tags_message),
                modifier = Modifier.padding(padding).padding(top = 64.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(SketchItemGap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(tags, key = { it.first.id }) { (tag, count) ->
                SketchCard(
                    onClick = { dialog = TagDialog.Edit(tag) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        TagDot(Color(tag.color), Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.documents_count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { dialog = TagDialog.Delete(tag) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        TagDialog.Create -> TagEditorDialog(
            title = stringResource(R.string.new_tag),
            initialColor = TagRepository.PALETTE[tags.size % TagRepository.PALETTE.size],
            onDismiss = { dialog = null },
            onConfirm = { name, color -> viewModel.create(name, color); dialog = null },
        )
        is TagDialog.Edit -> TagEditorDialog(
            title = stringResource(R.string.edit_tag),
            initialName = current.tag.name,
            initialColor = current.tag.color,
            onDismiss = { dialog = null },
            onConfirm = { name, color -> viewModel.update(current.tag.copy(name = name, color = color)); dialog = null },
        )
        is TagDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.delete_tag_title),
            message = stringResource(R.string.delete_tag_message, current.tag.name),
            onDismiss = { dialog = null },
            onConfirm = { viewModel.delete(current.tag); dialog = null },
        )
        null -> Unit
    }
}
