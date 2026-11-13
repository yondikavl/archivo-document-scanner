package com.yondex.archivo.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yondex.archivo.R
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.ui.components.SketchChip
import com.yondex.archivo.ui.components.SketchChipGap
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchItemGap
import com.yondex.archivo.ui.components.SketchOutlinedButton
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.SketchSheetDragHandle
import com.yondex.archivo.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    initial: ExportOptions,
    exporting: Boolean,
    canSaveImages: Boolean,
    onDismiss: () -> Unit,
    onShare: (ExportOptions) -> Unit,
    onSave: (ExportOptions) -> Unit,
) {
    var options by remember { mutableStateOf(initial) }
    val isImage = options.format == ExportFormat.JPG || options.format == ExportFormat.PNG
    val canSave = !isImage || canSaveImages
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
            verticalArrangement = Arrangement.spacedBy(SketchItemGap),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.export_document), style = MaterialTheme.typography.titleLarge)

            OptionGroup(stringResource(R.string.format), ExportFormat.entries, options.format, { it.label() }) {
                options = options.copy(format = it)
            }
            if (options.format == ExportFormat.PDF) {
                OptionGroup(stringResource(R.string.page_size), PdfPageSize.entries, options.pageSize, { it.label() }) {
                    options = options.copy(pageSize = it)
                }
                OptionGroup(stringResource(R.string.quality), ExportQuality.entries, options.quality, { it.label() }) {
                    options = options.copy(quality = it)
                }
            }
            if (options.format == ExportFormat.TXT) {
                Text(
                    stringResource(R.string.export_txt_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(SketchItemGap), modifier = Modifier.fillMaxWidth()) {
                SketchOutlinedButton(
                    enabled = !exporting && canSave,
                    onClick = { onSave(options) },
                    modifier = Modifier.weight(1f),
                    fillWidth = true,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isImage) R.string.to_gallery else R.string.action_save))
                }
                SketchFilledButton(
                    enabled = !exporting,
                    onClick = { onShare(options) },
                    modifier = Modifier.weight(1f),
                    fillWidth = true,
                ) {
                    if (exporting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_export),
                            contentDescription = null,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ExportFormat.label(): String = when (this) {
    ExportFormat.PDF -> "PDF"
    ExportFormat.JPG -> "JPG"
    ExportFormat.PNG -> "PNG"
    ExportFormat.TXT -> stringResource(R.string.format_text)
}

@Composable
private fun <T> OptionGroup(
    title: String,
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(SketchChipGap)) {
            values.forEach { value ->
                SketchChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
            }
        }
    }
}
