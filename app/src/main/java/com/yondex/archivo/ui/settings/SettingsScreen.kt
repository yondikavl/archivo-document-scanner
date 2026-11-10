package com.yondex.archivo.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.BuildConfig
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.domain.model.AppSettings
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.domain.model.ThemeMode
import com.yondex.archivo.ui.crop.label
import com.yondex.archivo.ui.document.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { repository.setDynamicColor(value) }
    fun setDefaultFilter(value: PageFilter) = viewModelScope.launch { repository.setDefaultFilter(value) }
    fun setPdfPageSize(value: PdfPageSize) = viewModelScope.launch { repository.setPdfPageSize(value) }
    fun setExportQuality(value: ExportQuality) = viewModelScope.launch { repository.setExportQuality(value) }
    fun setAutoOcr(value: Boolean) = viewModelScope.launch { repository.setAutoOcr(value) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Tampilan")
            ChoiceRow("Tema", ThemeMode.entries, settings.themeMode, { it.label }, viewModel::setThemeMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Warna dinamis") },
                    supportingContent = { Text("Ikuti warna wallpaper (nonaktifkan untuk tetap pakai hijau Archivo)") },
                    trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
                )
            }
            HorizontalDivider()

            Section("Scan")
            ChoiceRow("Filter bawaan", PageFilter.entries, settings.defaultFilter, { it.label }, viewModel::setDefaultFilter)
            ListItem(
                headlineContent = { Text("OCR otomatis") },
                supportingContent = { Text("Ekstrak teks setiap halaman setelah disimpan") },
                trailingContent = { Switch(checked = settings.autoOcr, onCheckedChange = viewModel::setAutoOcr) },
            )
            HorizontalDivider()

            Section("Export PDF")
            ChoiceRow("Ukuran halaman", PdfPageSize.entries, settings.pdfPageSize, { it.label }, viewModel::setPdfPageSize)
            ChoiceRow("Kualitas", ExportQuality.entries, settings.exportQuality, { it.label }, viewModel::setExportQuality)
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Versi aplikasi") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> ChoiceRow(title: String, values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            values.forEach { value ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
            }
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Ikuti sistem"
        ThemeMode.LIGHT -> "Terang"
        ThemeMode.DARK -> "Gelap"
    }
