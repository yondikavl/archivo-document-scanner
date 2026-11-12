package com.yondex.archivo.ui.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.BuildConfig
import com.yondex.archivo.R
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.domain.model.AppLanguage
import com.yondex.archivo.domain.model.AppSettings
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.domain.model.ThemeMode
import com.yondex.archivo.ui.components.SketchShape
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.label
import com.yondex.archivo.ui.legal.LegalDocument
import com.yondex.archivo.ui.legal.LegalDocumentSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }
    fun setAppLanguage(value: AppLanguage) = viewModelScope.launch { repository.setAppLanguage(value) }
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
    var legalDoc by remember { mutableStateOf<LegalDocument?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(
                title = stringResource(R.string.section_appearance),
                iconRes = R.drawable.ic_appearance,
            ) {
                OptionSetting(
                    title = stringResource(R.string.language),
                    iconRes = R.drawable.ic_languange,
                    values = AppLanguage.entries,
                    selected = settings.appLanguage,
                    label = { it.label() },
                    onSelect = viewModel::setAppLanguage,
                )
                SettingsDivider()
                OptionSetting(
                    title = stringResource(R.string.theme),
                    iconRes = R.drawable.ic_theme,
                    values = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.label() },
                    onSelect = viewModel::setThemeMode,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsDivider()
                    SwitchSetting(
                        title = stringResource(R.string.dynamic_color),
                        summary = stringResource(R.string.dynamic_color_summary),
                        iconRes = R.drawable.ic_dynamic_color,
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            SettingsGroup(
                title = stringResource(R.string.section_scan),
                iconRes = R.drawable.ic_scanner,
            ) {
                OptionSetting(
                    title = stringResource(R.string.default_filter),
                    values = PageFilter.entries,
                    selected = settings.defaultFilter,
                    label = { it.label() },
                    onSelect = viewModel::setDefaultFilter,
                )
                SettingsDivider()
                SwitchSetting(
                    title = stringResource(R.string.auto_ocr),
                    summary = stringResource(R.string.auto_ocr_summary),
                    iconRes = R.drawable.ic_ocr,
                    checked = settings.autoOcr,
                    onCheckedChange = viewModel::setAutoOcr,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.section_export_pdf),
                iconRes = R.drawable.ic_document,
            ) {
                OptionSetting(
                    title = stringResource(R.string.page_size),
                    values = PdfPageSize.entries,
                    selected = settings.pdfPageSize,
                    label = { it.label() },
                    onSelect = viewModel::setPdfPageSize,
                )
                SettingsDivider()
                OptionSetting(
                    title = stringResource(R.string.quality),
                    values = ExportQuality.entries,
                    selected = settings.exportQuality,
                    label = { it.label() },
                    onSelect = viewModel::setExportQuality,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.section_legal),
                iconRes = R.drawable.ic_guard,
            ) {
                ClickSetting(
                    title = stringResource(R.string.privacy_policy),
                    iconRes = R.drawable.ic_guard,
                    onClick = { legalDoc = LegalDocument.Privacy },
                )
                SettingsDivider()
                ClickSetting(
                    title = stringResource(R.string.terms_of_use),
                    iconRes = R.drawable.ic_about,
                    onClick = { legalDoc = LegalDocument.Terms },
                )
            }

            SettingsGroup(
                title = stringResource(R.string.section_about),
                iconRes = R.drawable.ic_about,
                framed = false,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_archivo),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(SketchShapeLarge),
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        stringResource(R.string.about_trust),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        stringResource(R.string.about_trust_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        stringResource(R.string.pdf_annotations_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(R.string.yondex_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.yondex_copyright, Year.now().value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    legalDoc?.let { doc ->
        LegalDocumentSheet(document = doc, onDismiss = { legalDoc = null })
    }
}

@Composable
private fun ClickSetting(
    title: String,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (iconRes != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(40.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    @DrawableRes iconRes: Int,
    framed: Boolean = true,
    content: @Composable () -> Unit,
) {
    SettingsGroup(
        title = title,
        painter = painterResource(iconRes),
        framed = framed,
        content = content,
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    framed: Boolean = true,
    content: @Composable () -> Unit,
) {
    SettingsGroup(
        title = title,
        painter = rememberVectorPainter(icon),
        framed = framed,
        content = content,
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    painter: Painter,
    framed: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (framed) {
            Surface(
                shape = SketchShapeLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    content = { content() },
                )
            }
        } else {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.background,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSetting(
    title: String,
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    @DrawableRes iconRes: Int? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = label(selected)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (iconRes != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(40.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }

    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            shape = SketchShapeLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = null,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                values.forEach { value ->
                    val isSelected = value == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SketchShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable {
                                onSelect(value)
                                open = false
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            label(value),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes iconRes: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (iconRes != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(40.dp)
                    .clip(SketchShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
