package com.yondex.archivo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yondex.archivo.data.local.prefs.SettingsRepository
import com.yondex.archivo.domain.model.AppLanguage
import com.yondex.archivo.domain.model.AppSettings
import com.yondex.archivo.domain.model.ThemeMode
import com.yondex.archivo.ui.LocaleHelper
import com.yondex.archivo.ui.navigation.ArchivoNavHost
import com.yondex.archivo.ui.theme.ArchivoTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: AppSettings()
            val dark = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ArchivoTheme(darkTheme = dark, dynamicColor = current.dynamicColor) {
                // Soft locale swap via CompositionLocals only.
                // Avoid key(language) + setApplicationLocales recreate — both reset nav to Home.
                LocalizedContent(current.appLanguage) {
                    ArchivoNavHost()
                }
            }
        }
    }
}

@Composable
private fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(language, base) { LocaleHelper.wrap(base, language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
