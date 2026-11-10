package com.yondex.archivo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = ArchivoGreen,
    onPrimary = Color.White,
    primaryContainer = ArchivoGreenContainer,
    onPrimaryContainer = ArchivoOnGreenContainer,
    secondary = ArchivoSecondary,
    onSecondary = Color.White,
    secondaryContainer = ArchivoSecondaryContainer,
    onSecondaryContainer = ArchivoOnGreenContainer,
    tertiary = ArchivoTertiary,
    onTertiary = Color.White,
    tertiaryContainer = ArchivoTertiaryContainer,
    onTertiaryContainer = ArchivoOnGreenContainer,
    background = ArchivoBackground,
    onBackground = Color(0xFF15201A),
    surface = ArchivoSurface,
    onSurface = Color(0xFF15201A),
    surfaceVariant = ArchivoSurfaceVariant,
    onSurfaceVariant = Color(0xFF3F4F46),
    outline = ArchivoOutline,
    outlineVariant = Color(0xFFBCC9C1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2A322D),
    inverseOnSurface = Color(0xFFEFF5F0),
    inversePrimary = ArchivoGreen80,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F7F3),
    surfaceContainer = Color(0xFFEBF3EE),
    surfaceContainerHigh = Color(0xFFE4EEE8),
    surfaceContainerHighest = Color(0xFFDEE8E1),
)

private val DarkColorScheme = darkColorScheme(
    primary = ArchivoGreen80,
    onPrimary = ArchivoOnGreenContainer,
    primaryContainer = ArchivoGreenContainerDark,
    onPrimaryContainer = ArchivoOnGreenContainerDark,
    secondary = ArchivoSecondary80,
    onSecondary = Color(0xFF0E2A1C),
    secondaryContainer = ArchivoSecondaryContainerDark,
    onSecondaryContainer = ArchivoSecondary80,
    tertiary = ArchivoTertiary80,
    onTertiary = Color(0xFF0E2A1C),
    tertiaryContainer = ArchivoTertiaryContainerDark,
    onTertiaryContainer = ArchivoTertiary80,
    background = ArchivoBackgroundDark,
    onBackground = Color(0xFFE2EBE5),
    surface = ArchivoSurfaceDark,
    onSurface = Color(0xFFE2EBE5),
    surfaceVariant = ArchivoSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBCC9C1),
    outline = ArchivoOutlineDark,
    outlineVariant = Color(0xFF3A463F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE2EBE5),
    inverseOnSurface = Color(0xFF2A322D),
    inversePrimary = ArchivoGreen,
    surfaceContainerLowest = Color(0xFF0B100D),
    surfaceContainerLow = Color(0xFF181F1B),
    surfaceContainer = Color(0xFF1C241F),
    surfaceContainerHigh = Color(0xFF262E29),
    surfaceContainerHighest = Color(0xFF313934),
)

@Composable
fun ArchivoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Prefer brand green over wallpaper-based dynamic color by default. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ArchivoShapes,
        content = content,
    )
}
