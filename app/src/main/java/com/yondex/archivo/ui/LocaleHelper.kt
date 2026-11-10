package com.yondex.archivo.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yondex.archivo.domain.model.AppLanguage
import java.util.Locale

object LocaleHelper {
    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            // BCP-47 "id"; also matches values-id / values-in.
            AppLanguage.INDONESIAN -> LocaleListCompat.forLanguageTags("id")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Keeps the Activity as [ContextWrapper] base (required by Hilt ViewModels)
     * while serving strings/resources from the selected locale.
     */
    fun wrap(context: Context, language: AppLanguage): Context {
        if (language == AppLanguage.SYSTEM) return context
        val locale = localeFor(language)
        val config = Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        val localized = context.createConfigurationContext(config)
        return object : ContextWrapper(context) {
            override fun getResources(): Resources = localized.resources
            override fun getAssets(): AssetManager = localized.assets
        }
    }

    fun localeFor(language: AppLanguage): Locale = when (language) {
        AppLanguage.SYSTEM -> Locale.getDefault()
        AppLanguage.ENGLISH -> Locale.forLanguageTag("en")
        AppLanguage.INDONESIAN -> Locale.forLanguageTag("id")
    }
}
