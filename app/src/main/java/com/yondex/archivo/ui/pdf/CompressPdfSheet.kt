package com.yondex.archivo.ui.pdf

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yondex.archivo.R
import com.yondex.archivo.data.repository.formatFileSize
import com.yondex.archivo.pdf.PdfCompressor
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.SketchSheetDragHandle
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressPdfSheet(
    currentBytes: Long,
    compressing: Boolean,
    onDismiss: () -> Unit,
    onCompress: (Long) -> Unit,
) {
    val minTarget = PdfCompressor.TARGET_100KB.toFloat()
    val maxTarget = remember(currentBytes) {
        max(
            minTarget,
            min(
                (currentBytes - 1024L).coerceAtLeast(minTarget.toLong()).toFloat(),
                (5L * 1024L * 1024L).toFloat(),
            ),
        )
    }
    var targetBytes by remember(currentBytes) {
        mutableFloatStateOf(
            min(
                maxTarget,
                max(minTarget, (currentBytes / 2f).coerceAtLeast(minTarget)),
            ),
        )
    }
    val targetLabel = formatFileSize(targetBytes.toLong())
    val canCompress = currentBytes > PdfCompressor.TARGET_100KB &&
        currentBytes > targetBytes.toLong() &&
        maxTarget > minTarget

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
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.compress_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.compress_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.compress_current_size, formatFileSize(currentBytes)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.compress_target_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.compress_target_value, targetLabel),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (canCompress) {
                Slider(
                    value = targetBytes,
                    onValueChange = { targetBytes = it },
                    valueRange = minTarget..maxTarget,
                    enabled = !compressing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.compress_min_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatFileSize(maxTarget.toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.compress_already_small),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            SketchFilledButton(
                onClick = { onCompress(targetBytes.toLong()) },
                enabled = !compressing && canCompress,
                fillWidth = true,
            ) {
                if (compressing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_compress),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(R.string.compress_run, targetLabel))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
