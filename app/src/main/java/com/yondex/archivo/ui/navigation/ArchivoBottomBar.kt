package com.yondex.archivo.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yondex.archivo.R
import com.yondex.archivo.ui.components.SketchBottomBar
import com.yondex.archivo.ui.components.SketchBottomNavItem

enum class MainTab {
    Home,
    Scan,
    Qr,
    Settings,
}

private data class TabSpec(
    val tab: MainTab,
    @DrawableRes val iconRes: Int,
    val labelRes: Int,
)

@Composable
fun ArchivoBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        TabSpec(MainTab.Home, R.drawable.ic_home, R.string.nav_home),
        TabSpec(MainTab.Scan, R.drawable.ic_camera, R.string.nav_scan),
        TabSpec(MainTab.Qr, R.drawable.ic_qrcode, R.string.nav_qr),
        TabSpec(MainTab.Settings, R.drawable.ic_settings, R.string.nav_settings),
    )
    SketchBottomBar(modifier = modifier.fillMaxWidth()) {
        tabs.forEach { spec ->
            SketchBottomNavItem(
                selected = selected == spec.tab,
                onClick = { onSelect(spec.tab) },
                iconRes = spec.iconRes,
                label = stringResource(spec.labelRes),
            )
        }
    }
}
