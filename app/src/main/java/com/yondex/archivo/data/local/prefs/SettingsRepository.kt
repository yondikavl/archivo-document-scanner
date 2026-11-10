package com.yondex.archivo.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yondex.archivo.domain.model.AppLanguage
import com.yondex.archivo.domain.model.AppSettings
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.domain.model.SortOrder
import com.yondex.archivo.domain.model.ThemeMode
import com.yondex.archivo.domain.model.ViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = prefs[Keys.THEME].toEnum(defaults.themeMode),
            appLanguage = prefs[Keys.LANGUAGE].toEnum(defaults.appLanguage),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            defaultFilter = prefs[Keys.DEFAULT_FILTER].toEnum(defaults.defaultFilter),
            pdfPageSize = prefs[Keys.PDF_PAGE_SIZE].toEnum(defaults.pdfPageSize),
            exportQuality = prefs[Keys.EXPORT_QUALITY].toEnum(defaults.exportQuality),
            autoOcr = prefs[Keys.AUTO_OCR] ?: defaults.autoOcr,
            viewMode = prefs[Keys.VIEW_MODE].toEnum(defaults.viewMode),
            sortOrder = prefs[Keys.SORT_ORDER].toEnum(defaults.sortOrder),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = put(Keys.THEME, value.name)
    suspend fun setAppLanguage(value: AppLanguage) = put(Keys.LANGUAGE, value.name)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC_COLOR, value)
    suspend fun setDefaultFilter(value: PageFilter) = put(Keys.DEFAULT_FILTER, value.name)
    suspend fun setPdfPageSize(value: PdfPageSize) = put(Keys.PDF_PAGE_SIZE, value.name)
    suspend fun setExportQuality(value: ExportQuality) = put(Keys.EXPORT_QUALITY, value.name)
    suspend fun setAutoOcr(value: Boolean) = put(Keys.AUTO_OCR, value)
    suspend fun setViewMode(value: ViewMode) = put(Keys.VIEW_MODE, value.name)
    suspend fun setSortOrder(value: SortOrder) = put(Keys.SORT_ORDER, value.name)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val PDF_PAGE_SIZE = stringPreferencesKey("pdf_page_size")
        val EXPORT_QUALITY = stringPreferencesKey("export_quality")
        val AUTO_OCR = booleanPreferencesKey("auto_ocr")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
