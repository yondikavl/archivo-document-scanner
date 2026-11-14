package com.yondex.archivo.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space to keep scroll content clear of the floating rounded bottom nav. */
val LocalBottomNavInset = compositionLocalOf { 0.dp }

/** Tab row height inside [com.yondex.archivo.ui.components.SketchBottomBar]. */
val BottomNavTabHeight = 72.dp

@Composable
fun bottomNavInset(visible: Boolean): Dp {
    if (!visible) return 0.dp
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    return BottomNavTabHeight + with(density) { navBottom.toDp() }
}
