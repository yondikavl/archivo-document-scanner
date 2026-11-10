package com.yondex.archivo.domain.model

import kotlin.math.hypot

/** A point in normalized image coordinates, where (0,0) is top-left and (1,1) is bottom-right. */
data class NPoint(val x: Float, val y: Float) {
    fun distanceTo(other: NPoint): Float = hypot(x - other.x, y - other.y)
}

/** Document corners in normalized coordinates, ordered top-left, top-right, bottom-right, bottom-left. */
data class Quad(val points: List<NPoint>) {
    init {
        require(points.size == 4) { "Quad needs exactly 4 points" }
    }

    val topLeft get() = points[0]
    val topRight get() = points[1]
    val bottomRight get() = points[2]
    val bottomLeft get() = points[3]

    val isFull: Boolean get() = this == FULL

    fun withPoint(index: Int, point: NPoint): Quad =
        Quad(points.toMutableList().also { it[index] = point.clamped() })

    fun encode(): String = points.joinToString(";") { "${it.x},${it.y}" }

    companion object {
        val FULL = Quad(listOf(NPoint(0f, 0f), NPoint(1f, 0f), NPoint(1f, 1f), NPoint(0f, 1f)))

        fun decode(value: String?): Quad {
            if (value.isNullOrBlank()) return FULL
            return runCatching {
                Quad(value.split(";").map { pair ->
                    val (x, y) = pair.split(",").map { it.toFloat() }
                    NPoint(x, y)
                })
            }.getOrDefault(FULL)
        }

        /** Orders four arbitrary corners as top-left, top-right, bottom-right, bottom-left. */
        fun fromUnordered(points: List<NPoint>): Quad {
            val tl = points.minBy { it.x + it.y }
            val br = points.maxBy { it.x + it.y }
            val tr = points.minBy { it.y - it.x }
            val bl = points.maxBy { it.y - it.x }
            return Quad(listOf(tl, tr, br, bl).map { it.clamped() })
        }
    }
}

private fun NPoint.clamped() = NPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
