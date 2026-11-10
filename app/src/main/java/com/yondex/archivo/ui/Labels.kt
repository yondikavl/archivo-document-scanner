package com.yondex.archivo.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yondex.archivo.R
import com.yondex.archivo.domain.model.AppLanguage
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.domain.model.SortOrder
import com.yondex.archivo.domain.model.ThemeMode

@Composable
fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.INDONESIAN -> R.string.language_indonesian
    }
)

@Composable
fun SortOrder.label(): String = stringResource(
    when (this) {
        SortOrder.UPDATED_DESC -> R.string.sort_updated
        SortOrder.CREATED_DESC -> R.string.sort_created
        SortOrder.NAME_ASC -> R.string.sort_name
        SortOrder.PAGES_DESC -> R.string.sort_pages
    }
)

@Composable
fun PageFilter.label(): String = stringResource(
    when (this) {
        PageFilter.ORIGINAL -> R.string.filter_original
        PageFilter.ENHANCE -> R.string.filter_enhance
        PageFilter.GRAYSCALE -> R.string.filter_grayscale
        PageFilter.BLACK_WHITE -> R.string.filter_bw
    }
)

@Composable
fun PdfPageSize.label(): String = when (this) {
    PdfPageSize.A4 -> "A4"
    PdfPageSize.LETTER -> "Letter"
    PdfPageSize.FIT_IMAGE -> stringResource(R.string.page_size_fit)
}

@Composable
fun ExportQuality.label(): String = stringResource(
    when (this) {
        ExportQuality.LOW -> R.string.quality_low
        ExportQuality.MEDIUM -> R.string.quality_medium
        ExportQuality.HIGH -> R.string.quality_high
    }
)

fun Context.str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) getString(id) else getString(id, *args)
