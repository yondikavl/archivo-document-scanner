package com.yondex.archivo.ui.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yondex.archivo.domain.model.NPoint
import com.yondex.archivo.domain.model.Quad
import kotlin.math.min
import kotlin.math.roundToInt

/** Shows [image] with four draggable corner handles and a magnifier while dragging. */
@Composable
fun CropEditor(
    image: ImageBitmap,
    quad: Quad,
    accent: Color,
    onCornerMoved: (index: Int, point: NPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val insetPx = with(density) { 24.dp.toPx() }
    val touchRadiusPx = with(density) { 48.dp.toPx() }
    val currentQuad by rememberUpdatedState(quad)
    val currentOnMoved by rememberUpdatedState(onCornerMoved)
    var activeCorner by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier.pointerInput(image) {
            detectDragGestures(
                onDragStart = { start ->
                    val rect = fitRect(size.width.toFloat(), size.height.toFloat(), image, insetPx)
                    val corners = currentQuad.points.map { rect.toView(it) }
                    val nearest = corners.indices.minBy { (corners[it] - start).getDistance() }
                    if ((corners[nearest] - start).getDistance() <= touchRadiusPx) {
                        activeCorner = nearest
                        dragPosition = corners[nearest]
                    }
                },
                onDrag = { change, amount ->
                    if (activeCorner >= 0) {
                        change.consume()
                        val rect = fitRect(size.width.toFloat(), size.height.toFloat(), image, insetPx)
                        dragPosition = Offset(
                            (dragPosition.x + amount.x).coerceIn(rect.left, rect.right),
                            (dragPosition.y + amount.y).coerceIn(rect.top, rect.bottom),
                        )
                        currentOnMoved(activeCorner, rect.toNormalized(dragPosition))
                    }
                },
                onDragEnd = { activeCorner = -1 },
                onDragCancel = { activeCorner = -1 },
            )
        },
    ) {
        val rect = fitRect(size.width, size.height, image, insetPx)
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        )

        val corners = quad.points.map { rect.toView(it) }
        val quadPath = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            corners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(rect)
            addPath(quadPath)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.45f))
        drawPath(quadPath, accent, style = Stroke(width = 2.dp.toPx()))
        corners.forEachIndexed { index, point ->
            drawCircle(Color.White, radius = 12.dp.toPx(), center = point)
            drawCircle(accent, radius = if (index == activeCorner) 9.dp.toPx() else 7.dp.toPx(), center = point)
        }

        if (activeCorner >= 0) {
            val radius = 56.dp.toPx()
            val margin = 12.dp.toPx() + radius
            val center = if (dragPosition.x < size.width / 2 && dragPosition.y < margin * 2) {
                Offset(size.width - margin, margin)
            } else {
                Offset(margin, margin)
            }
            val zoom = 2.5f
            val scale = rect.width / image.width * zoom
            val point = rect.toNormalized(dragPosition)
            val circle = Path().apply { addOval(Rect(center, radius)) }
            clipPath(circle) {
                drawRect(Color.Black, topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2))
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (center.x - point.x * image.width * scale).roundToInt(),
                        (center.y - point.y * image.height * scale).roundToInt(),
                    ),
                    dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
                )
            }
            drawCircle(Color.White, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
            val cross = 8.dp.toPx()
            drawLine(accent, center - Offset(cross, 0f), center + Offset(cross, 0f), strokeWidth = 2.dp.toPx())
            drawLine(accent, center - Offset(0f, cross), center + Offset(0f, cross), strokeWidth = 2.dp.toPx())
        }
    }
}

private fun fitRect(width: Float, height: Float, image: ImageBitmap, inset: Float): Rect {
    val availableWidth = (width - inset * 2).coerceAtLeast(1f)
    val availableHeight = (height - inset * 2).coerceAtLeast(1f)
    val scale = min(availableWidth / image.width, availableHeight / image.height)
    val drawWidth = image.width * scale
    val drawHeight = image.height * scale
    val left = (width - drawWidth) / 2f
    val top = (height - drawHeight) / 2f
    return Rect(left, top, left + drawWidth, top + drawHeight)
}

private fun Rect.toView(point: NPoint) = Offset(left + point.x * width, top + point.y * height)

private fun Rect.toNormalized(offset: Offset) = NPoint((offset.x - left) / width, (offset.y - top) / height)
