package com.yondex.archivo.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.R
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.ui.components.ConfirmDialog
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.SketchSheetDragHandle
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.sketchBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    onBack: () -> Unit,
    onCrop: (pageId: Long) -> Unit,
    viewModel: PageViewerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val title by viewModel.documentTitle.collectAsStateWithLifecycle()
    val busy by viewModel.busyPages.collectAsStateWithLifecycle()
    var showText by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pageZoomed by remember { mutableStateOf(false) }
    val textCopied = stringResource(R.string.text_copied)

    LaunchedEffect(Unit) { viewModel.shareEvents.collect { context.startActivity(it) } }

    val list = pages
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LaunchedEffect(list.isEmpty()) {
        if (list.isEmpty()) onBack()
    }
    if (list.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = viewModel.initialIndex.coerceIn(0, list.lastIndex),
    ) { list.size }

    LaunchedEffect(list.size, list.lastOrNull()?.id) {
        if (pagerState.currentPage > list.lastIndex) {
            pagerState.scrollToPage(list.lastIndex)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        pageZoomed = false
    }

    val current by remember(list) {
        derivedStateOf { list.getOrNull(pagerState.currentPage) ?: list.last() }
    }
    val currentPage = rememberUpdatedState(current)
    val pageBusy = current.id in busy

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${pagerState.currentPage + 1} / ${list.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Sharp.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            PageActionBar(
                busy = pageBusy,
                onText = { showText = true },
                onCrop = { onCrop(currentPage.value.id) },
                onRotate = { viewModel.rotate(currentPage.value) },
                onDelete = { confirmDelete = true },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            HorizontalPager(
                state = pagerState,
                key = { list[it].id },
                beyondViewportPageCount = 1,
                userScrollEnabled = !pageZoomed,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val page = list[index]
                val isCurrent = index == pagerState.currentPage
                ZoomableImage(
                    path = page.processedPath,
                    contentDescription = stringResource(R.string.page_cd, index + 1),
                    modifier = Modifier.fillMaxSize(),
                    onZoomChanged = { zoomed ->
                        if (isCurrent) pageZoomed = zoomed
                    },
                )
            }
            if (pageBusy) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    if (showText) {
        OcrTextSheet(
            page = current,
            onDismiss = { showText = false },
            onCopy = { text ->
                copyToClipboard(context, text)
                Toast.makeText(context, textCopied, Toast.LENGTH_SHORT).show()
            },
            onShare = viewModel::shareText,
            onSave = { viewModel.saveText(current, it) },
            onRunOcr = { viewModel.runOcr(current) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_page_title),
            message = stringResource(R.string.delete_page_message, pagerState.currentPage + 1),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete(current)
                confirmDelete = false
            },
        )
    }
}

@Composable
private fun PageActionBar(
    busy: Boolean,
    onText: () -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.sketchBorder(RectangleShape, SketchInk, SketchStroke),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageAction(
                    iconRes = R.drawable.ic_ocr,
                    label = stringResource(R.string.action_text),
                    onClick = onText,
                )
                PageAction(
                    iconRes = R.drawable.ic_crop,
                    label = stringResource(R.string.action_crop),
                    onClick = onCrop,
                )
                PageAction(
                    iconRes = R.drawable.ic_rotate,
                    label = stringResource(R.string.action_rotate),
                    enabled = !busy,
                    onClick = onRotate,
                )
                PageAction(
                    iconRes = R.drawable.ic_delete,
                    label = stringResource(R.string.action_delete),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PageAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    PageAction(
        painter = rememberVectorPainter(icon),
        label = label,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun PageAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    PageAction(
        painter = painterResource(iconRes),
        label = label,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun PageAction(
    painter: Painter,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
            Icon(painter = painter, contentDescription = label)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrTextSheet(
    page: Page,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onSave: (String) -> Unit,
    onRunOcr: () -> Unit,
) {
    var editing by remember(page.id) { mutableStateOf(false) }
    var draft by remember(page.id, page.ocrText) { mutableStateOf(page.ocrText.orEmpty()) }
    val text = page.ocrText.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SketchShapeLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SketchSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.page_text),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!editing && text.isNotEmpty()) {
                    IconButton(onClick = { onCopy(text) }) {
                        Icon(Icons.Sharp.ContentCopy, contentDescription = stringResource(R.string.action_text))
                    }
                    IconButton(onClick = { onShare(text) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_export),
                            contentDescription = stringResource(R.string.action_share),
                        )
                    }
                }
                if (!editing && page.ocrStatus == OcrStatus.DONE) {
                    IconButton(onClick = { editing = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rename),
                            contentDescription = stringResource(R.string.action_rename),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            when {
                editing -> {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        shape = RectangleShape,
                        colors = OutlinedTextFieldDefaults.colors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        TextButton(onClick = { editing = false; draft = text }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        SketchFilledButton(onClick = { onSave(draft); editing = false }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_save),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
                page.ocrStatus == OcrStatus.PENDING || page.ocrStatus == OcrStatus.RUNNING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.recognizing_text))
                    }
                }
                page.ocrStatus == OcrStatus.DONE && text.isNotEmpty() -> {
                    SelectionContainer(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    Text(
                        stringResource(
                            if (page.ocrStatus == OcrStatus.FAILED) R.string.ocr_failed else R.string.no_ocr_text,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    SketchFilledButton(onClick = onRunOcr) {
                        Text(stringResource(R.string.run_ocr))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Archivo", text))
}
