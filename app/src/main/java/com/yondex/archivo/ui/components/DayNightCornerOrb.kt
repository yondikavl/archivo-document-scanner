package com.yondex.archivo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.yondex.archivo.ui.theme.ArchivoTertiary
import com.yondex.archivo.ui.theme.ArchivoTertiary80
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorative sun (light) / moon (dark) peeking from the top-end corner.
 * Non-interactive; sits behind screen content.
 */
@Composable
fun DayNightCornerOrb(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (!visible) return
    val night = MaterialTheme.colorScheme.background.luminance() < 0.45f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
        contentAlignment = Alignment.TopEnd,
    ) {
        Canvas(
            Modifier
                .size(240.dp)
                .offset(x = 88.dp, y = (-64).dp),
        ) {
            if (night) drawOverflowMoon() else drawOverflowSun()
        }
    }
}

private fun DrawScope.drawOverflowSun() {
    val center = Offset(size.width * 0.42f, size.height * 0.58f)
    val radius = size.minDimension * 0.32f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ArchivoTertiary.copy(alpha = 0.55f),
                ArchivoTertiary.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 1.85f,
        ),
        radius = radius * 1.85f,
        center = center,
    )

    val rayLen = radius * 1.38f
    val rayInner = radius * 1.08f
    val stroke = 5.dp.toPx()
    for (i in 0 until 8) {
        val angle = (i * 45f) * (PI.toFloat() / 180f)
        val cosA = cos(angle)
        val sinA = sin(angle)
        drawLine(
            color = ArchivoTertiary.copy(alpha = 0.6f),
            start = Offset(center.x + cosA * rayInner, center.y + sinA * rayInner),
            end = Offset(center.x + cosA * rayLen, center.y + sinA * rayLen),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF6C2), ArchivoTertiary, Color(0xFFF5B700)),
            center = center - Offset(radius * 0.18f, radius * 0.18f),
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawOverflowMoon() {
    val center = Offset(size.width * 0.42f, size.height * 0.58f)
    val radius = size.minDimension * 0.34f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ArchivoTertiary80.copy(alpha = 0.28f),
                Color(0xFF7EC8FF).copy(alpha = 0.12f),
                Color.Transparent,
            ),
            center = center,
            radius = radius * 1.7f,
        ),
        radius = radius * 1.7f,
        center = center,
    )

    val cut = radius * 0.78f
    val cutCenter = Offset(center.x + radius * 0.38f, center.y - radius * 0.12f)
    val crescent = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
        addOval(Rect(cutCenter.x - cut, cutCenter.y - cut, cutCenter.x + cut, cutCenter.y + cut))
    }
    drawPath(
        path = crescent,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFF4F1E6), Color(0xFFD5D8E2), Color(0xFF9AA3B5)),
            center = center - Offset(radius * 0.25f, radius * 0.2f),
            radius = radius,
        ),
    )
}
