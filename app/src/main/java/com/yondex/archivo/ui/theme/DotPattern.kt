package com.yondex.archivo.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * App-wide paper background with a subtle dot grid.
 * Place once at the root; screens that use a transparent scaffold will show it.
 */
@Composable
fun ArchivoDottedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .dotPattern(
                background = background,
                dotColor = dotColor,
                spacing = 18.dp,
                radius = 1.1.dp,
            ),
        content = content,
    )
}

fun Modifier.dotPattern(
    background: Color,
    dotColor: Color,
    spacing: Dp = 18.dp,
    radius: Dp = 1.1.dp,
): Modifier = drawBehind {
    drawRect(background)
    val step = spacing.toPx().coerceAtLeast(1f)
    val r = radius.toPx()
    var y = step / 2f
    while (y < size.height + step) {
        var x = step / 2f
        while (x < size.width + step) {
            drawCircle(color = dotColor, radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
}
