package com.yondex.archivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Add
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material.icons.sharp.Folder
import androidx.compose.material.icons.sharp.Home
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.TagRepository
import com.yondex.archivo.data.repository.asTree
import com.yondex.archivo.domain.model.Folder
import com.yondex.archivo.domain.model.Tag

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(0, initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val valid = value.text.isNotBlank()
    val confirmText = confirmLabel ?: stringResource(R.string.action_save)
    SketchAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                shape = RectangleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(value.text.trim()) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val confirmText = confirmLabel ?: stringResource(R.string.action_delete)
    SketchAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    val tree = remember(folders) { folders.asTree() }
    SketchAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    FolderRow(
                        name = stringResource(R.string.home_no_folder),
                        depth = 0,
                        isRoot = true,
                        selected = currentFolderId == null,
                        onClick = { onPick(null) },
                    )
                }
                items(tree, key = { it.first.id }) { (folder, depth) ->
                    FolderRow(
                        name = folder.name,
                        depth = depth + 1,
                        isRoot = false,
                        selected = folder.id == currentFolderId,
                        onClick = { onPick(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FolderRow(name: String, depth: Int, isRoot: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (depth * 16).dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (isRoot) {
            Icon(Icons.Sharp.Home, contentDescription = null)
        } else {
            Icon(Icons.Sharp.Folder, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Text(name, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Sharp.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Lets the user pick tags. [initialSelected] are fully selected; [partial] are tags present on only
 * some of the edited documents and stay untouched unless the user toggles them.
 */
@Composable
fun TagPickerDialog(
    tags: List<Tag>,
    initialSelected: Set<Long>,
    partial: Set<Long> = emptySet(),
    onCreateTag: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (added: Set<Long>, removed: Set<Long>) -> Unit,
) {
    var selected by remember { mutableStateOf(initialSelected) }
    var undecided by remember { mutableStateOf(partial) }
    var newTag by remember { mutableStateOf("") }
    var pendingName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tags, pendingName) {
        val name = pendingName ?: return@LaunchedEffect
        tags.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let {
            selected = selected + it.id
            undecided = undecided - it.id
            pendingName = null
        }
    }

    SketchAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_tag)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text(stringResource(R.string.new_tag)) },
                        singleLine = true,
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = newTag.isNotBlank(),
                        onClick = {
                            pendingName = newTag.trim()
                            onCreateTag(newTag.trim())
                            newTag = ""
                        },
                    ) { Icon(Icons.Sharp.Add, contentDescription = stringResource(R.string.add_tag)) }
                }
                Spacer(Modifier.padding(4.dp))
                HorizontalDivider()
                if (tags.isEmpty()) {
                    Text(
                        stringResource(R.string.no_tags_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        val isUndecided = tag.id in undecided
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    undecided = undecided - tag.id
                                    selected = if (tag.id in selected && !isUndecided) selected - tag.id else selected + tag.id
                                },
                        ) {
                            Checkbox(
                                checked = tag.id in selected && !isUndecided,
                                onCheckedChange = null,
                                modifier = Modifier.padding(12.dp),
                            )
                            TagDot(Color(tag.color))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isUndecided) stringResource(R.string.tag_partial, tag.name) else tag.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val decided = tags.map { it.id }.filterNot { it in undecided }.toSet()
                val added = selected.intersect(decided)
                val removed = decided - selected
                onConfirm(added, removed)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TagEditorDialog(
    title: String,
    initialName: String = "",
    initialColor: Int = TagRepository.PALETTE.first(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    SketchAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name)) },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                TagRepository.PALETTE.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { option ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(option), CircleShape)
                                    .clickable { color = option },
                            ) {
                                if (option == color) Icon(Icons.Sharp.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), color) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
