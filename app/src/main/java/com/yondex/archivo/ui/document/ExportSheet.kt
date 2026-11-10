package com.yondex.archivo.ui.document

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.PdfPageSize

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text("Export dokumen", style = MaterialTheme.typography.titleLarge)

            OptionGroup("Format", ExportFormat.entries, options.format, { it.label }) {
                options = options.copy(format = it)
            }
            if (options.format == ExportFormat.PDF) {
                OptionGroup("Ukuran halaman", PdfPageSize.entries, options.pageSize, { it.label }) {
                    options = options.copy(pageSize = it)
                }
                OptionGroup("Kualitas", ExportQuality.entries, options.quality, { it.label }) {
                    options = options.copy(quality = it)
                }
            }
            if (options.format == ExportFormat.TXT) {
                Text(
                    "Berisi teks hasil OCR dari semua halaman.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !exporting && canSave,
                    onClick = { onSave(options) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isImage) "Ke Galeri" else "Simpan")
                }
                Button(
                    enabled = !exporting,
                    onClick = { onShare(options) },
                    modifier = Modifier.weight(1f),
                ) {
                    if (exporting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Bagikan")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun <T> OptionGroup(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
            }
        }
    }
}

val PdfPageSize.label: String
    get() = when (this) {
        PdfPageSize.A4 -> "A4"
        PdfPageSize.LETTER -> "Letter"
        PdfPageSize.FIT_IMAGE -> "Ikuti gambar"
    }

val ExportQuality.label: String
    get() = when (this) {
        ExportQuality.LOW -> "Rendah"
        ExportQuality.MEDIUM -> "Sedang"
        ExportQuality.HIGH -> "Tinggi"
    }
