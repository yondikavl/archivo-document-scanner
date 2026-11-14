package com.yondex.archivo.ui.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Rectangle with squircle (superellipse) corners — closer to iOS continuous corners
 * than a circular [RoundedCornerShape].
 *
 * [smoothing] 0 ≈ circular corners, 1 ≈ squarer squircle corners.
 */
data class SquircleShape(
    private val topStart: CornerSize,
    private val topEnd: CornerSize,
    private val bottomEnd: CornerSize,
    private val bottomStart: CornerSize,
    private val smoothing: Float = 0.72f,
) : Shape {

    constructor(corner: CornerSize, smoothing: Float = 0.72f) :
        this(corner, corner, corner, corner, smoothing)

    constructor(corner: Dp, smoothing: Float = 0.72f) :
        this(CornerSize(corner), smoothing)

    constructor(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp,
        smoothing: Float = 0.72f,
    ) : this(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart),
        smoothing,
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val smooth = smoothing.coerceIn(0f, 1f)
        // n=2 → circle quadrant, n≈5–6 → classic squircle corner
        val exponent = 2f + smooth * 3.5f
        val rtl = layoutDirection == LayoutDirection.Rtl
        val tl = (if (rtl) topEnd else topStart).toPx(size, density)
        val tr = (if (rtl) topStart else topEnd).toPx(size, density)
        val br = (if (rtl) bottomStart else bottomEnd).toPx(size, density)
        val bl = (if (rtl) bottomEnd else bottomStart).toPx(size, density)
        return Outline.Generic(createSquircleRectPath(size, tl, tr, br, bl, exponent))
    }
}

fun SquircleShape(percent: Int, smoothing: Float = 0.72f): SquircleShape =
    SquircleShape(CornerSize(percent), smoothing)

private fun createSquircleRectPath(
    size: Size,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    exponent: Float,
): Path {
    val w = size.width
    val h = size.height
    // Allow a single side to use the full edge length (e.g. top-only bottom bar).
    var tl = topLeft.coerceAtLeast(0f)
    var tr = topRight.coerceAtLeast(0f)
    var br = bottomRight.coerceAtLeast(0f)
    var bl = bottomLeft.coerceAtLeast(0f)
    if (tl + tr > w && tl + tr > 0f) {
        val s = w / (tl + tr)
        tl *= s
        tr *= s
    }
    if (bl + br > w && bl + br > 0f) {
        val s = w / (bl + br)
        bl *= s
        br *= s
    }
    if (tl + bl > h && tl + bl > 0f) {
        val s = h / (tl + bl)
        tl *= s
        bl *= s
    }
    if (tr + br > h && tr + br > 0f) {
        val s = h / (tr + br)
        tr *= s
        br *= s
    }

    val path = Path()
    // Begin at end of top-left corner on the top edge.
    if (tl > 0f) {
        path.moveTo(0f, tl)
        appendSuperellipseCorner(
            path = path,
            cx = tl,
            cy = tl,
            radius = tl,
            startDeg = 180f,
            endDeg = 270f,
            n = exponent,
        )
    } else {
        path.moveTo(0f, 0f)
    }

    path.lineTo(w - tr, 0f)
    if (tr > 0f) {
        appendSuperellipseCorner(
            path = path,
            cx = w - tr,
            cy = tr,
            radius = tr,
            startDeg = 270f,
            endDeg = 360f,
            n = exponent,
        )
    }

    path.lineTo(w, h - br)
    if (br > 0f) {
        appendSuperellipseCorner(
            path = path,
            cx = w - br,
            cy = h - br,
            radius = br,
            startDeg = 0f,
            endDeg = 90f,
            n = exponent,
        )
    }

    path.lineTo(bl, h)
    if (bl > 0f) {
        appendSuperellipseCorner(
            path = path,
            cx = bl,
            cy = h - bl,
            radius = bl,
            startDeg = 90f,
            endDeg = 180f,
            n = exponent,
        )
    }

    path.close()
    return path
}

/** Samples a quarter-superellipse from [startDeg] to [endDeg] (degrees, CCW from +x). */
private fun appendSuperellipseCorner(
    path: Path,
    cx: Float,
    cy: Float,
    radius: Float,
    startDeg: Float,
    endDeg: Float,
    n: Float,
    steps: Int = 12,
) {
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val deg = startDeg + (endDeg - startDeg) * t
        val rad = Math.toRadians(deg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val x = cx + radius * sign(c) * abs(c).pow(2f / n)
        val y = cy + radius * sign(s) * abs(s).pow(2f / n)
        path.lineTo(x, y)
    }
}
