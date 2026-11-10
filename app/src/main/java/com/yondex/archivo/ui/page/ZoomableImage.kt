package com.yondex.archivo.ui.page

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import java.io.File

/**
 * Image with pinch-to-zoom, pan and double-tap. Single-finger drags pass through to a parent
 * pager while the image is not zoomed.
 */
@Composable
fun ZoomableImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit = {},
) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val zoomed = scale > 1.01f

    LaunchedEffect(path) {
        onZoomChanged(false)
    }
    LaunchedEffect(zoomed) {
        onZoomChanged(zoomed)
    }

    fun clamp(value: Offset, currentScale: Float): Offset {
        if (size.width == 0 || size.height == 0 || currentScale <= 1f) return Offset.Zero
        val maxX = size.width * (currentScale - 1f) / 2f
        val maxY = size.height * (currentScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val imageRequest = remember(path, size.width, size.height) {
        val requestSize = if (size.width > 0 && size.height > 0) {
            // 2× viewport keeps pinch-zoom sharp without decoding full scan resolution.
            Size(
                (size.width * 2).coerceAtMost(4096),
                (size.height * 2).coerceAtMost(4096),
            )
        } else {
            Size.ORIGINAL
        }
        ImageRequest.Builder(context)
            .data(File(path))
            .size(requestSize)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .memoryCacheKey(path)
            .build()
    }

    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(path) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val target = 2.5f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        scale = target
                        offset = clamp((center - tap) * (target - 1f), target)
                    }
                })
            }
            .pointerInput(path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.size > 1
                        if (multiTouch || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = clamp(offset + event.calculatePan(), newScale)
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
