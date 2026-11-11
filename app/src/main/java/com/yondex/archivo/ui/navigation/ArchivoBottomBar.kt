package com.yondex.archivo.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.DocumentScanner
import androidx.compose.material.icons.sharp.Home
import androidx.compose.material.icons.sharp.QrCodeScanner
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yondex.archivo.R
import com.yondex.archivo.ui.components.SketchBottomBar
import com.yondex.archivo.ui.components.SketchBottomNavItem

enum class MainTab {
    Home,
    Qr,
    Scan,
    Settings,
}

private data class TabSpec(
    val tab: MainTab,
    val icon: ImageVector,
    val labelRes: Int,
)

@Composable
fun ArchivoBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        TabSpec(MainTab.Home, Icons.Sharp.Home, R.string.nav_home),
        TabSpec(MainTab.Qr, Icons.Sharp.QrCodeScanner, R.string.nav_qr),
        TabSpec(MainTab.Scan, Icons.Sharp.DocumentScanner, R.string.nav_scan),
        TabSpec(MainTab.Settings, Icons.Sharp.Settings, R.string.nav_settings),
    )
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        SketchBottomBar {
            tabs.forEach { spec ->
                SketchBottomNavItem(
                    selected = selected == spec.tab,
                    onClick = { onSelect(spec.tab) },
                    icon = spec.icon,
                    label = stringResource(spec.labelRes),
                )
            }
        }
    }
}
