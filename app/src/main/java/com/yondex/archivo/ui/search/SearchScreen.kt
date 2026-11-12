package com.yondex.archivo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.ArrowDropDown
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.data.repository.asTree
import com.yondex.archivo.data.repository.formatFileSize
import com.yondex.archivo.domain.model.LibraryFile
import com.yondex.archivo.domain.model.SearchResult
import com.yondex.archivo.ui.components.DocumentListItem
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.SketchCard
import com.yondex.archivo.ui.components.SketchChip
import com.yondex.archivo.ui.components.SketchChipGap
import com.yondex.archivo.ui.components.SketchDropdownMenu
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchShape
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.TagDot
import com.yondex.archivo.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenPdf: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val folderFilter by viewModel.folderFilter.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var folderMenu by remember { mutableStateOf(false) }
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Sharp.Close, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(SketchItemGap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SketchChipGap)) {
                    item {
                        Box {
                            SketchChip(
                                selected = folderFilter != null,
                                onClick = { folderMenu = true },
                                label = {
                                    Text(
                                        folders.firstOrNull { it.id == folderFilter }?.name
                                            ?: stringResource(R.string.all_folders),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_folder),
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = { Icon(Icons.Sharp.ArrowDropDown, contentDescription = null) },
                            )
                            SketchDropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.all_folders)) },
                                    onClick = { viewModel.setFolderFilter(null); folderMenu = false },
                                )
                                folders.asTree().forEach { (folder, depth) ->
                                    DropdownMenuItem(
                                        text = { Text("  ".repeat(depth) + folder.name) },
                                        onClick = { viewModel.setFolderFilter(folder.id); folderMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    items(tags, key = { it.id }) { tag ->
                        SketchChip(
                            selected = tag.id in tagFilter,
                            onClick = { viewModel.toggleTag(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = { TagDot(Color(tag.color)) },
                        )
                    }
                }
            }

            if (state.hasQuery && state.results.isEmpty()) {
                item {
                    EmptyState(
                        iconRes = R.drawable.ic_search,
                        title = stringResource(R.string.search_not_found),
                        message = stringResource(R.string.search_not_found_message, query),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            }

            items(
                state.results,
                key = {
                    when (it) {
                        is SearchResult.DocumentHit -> "doc_${it.document.id}"
                        is SearchResult.PdfHit -> "pdf_${it.file.id}"
                    }
                },
            ) { result ->
                when (result) {
                    is SearchResult.DocumentHit -> DocumentListItem(
                        document = result.document,
                        selected = false,
                        onClick = { onOpenDocument(result.document.id) },
                        onLongClick = {},
                        subtitle = result.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                            {
                                Text(
                                    highlightSnippet(snippet, highlight),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                    is SearchResult.PdfHit -> SearchPdfRow(
                        file = result.file,
                        onClick = { onOpenPdf(result.file.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPdfRow(file: LibraryFile, onClick: () -> Unit) {
    SketchCard(
        shape = SketchShapeLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Thumbnail(file.coverPath, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(stringResource(R.string.search_pdf_label))
                        if (file.pageCount > 0) {
                            append(" · ")
                            append(stringResource(R.string.pages_short, file.pageCount))
                        }
                        append(" · ")
                        append(formatFileSize(file.sizeBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun highlightSnippet(snippet: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    var rest = snippet.replace('\n', ' ')
    while (rest.isNotEmpty()) {
        val start = rest.indexOf(DocumentRepository.HIGHLIGHT_START)
        if (start < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, start))
        val end = rest.indexOf(DocumentRepository.HIGHLIGHT_END, start + 1).let { if (it < 0) rest.length else it }
        withStyle(style) { append(rest.substring(start + 1, end)) }
        rest = if (end + 1 <= rest.length) rest.substring(minOf(end + 1, rest.length)) else ""
    }
}
