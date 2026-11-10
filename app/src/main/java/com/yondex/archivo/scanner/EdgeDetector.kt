package com.yondex.archivo.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.yondex.archivo.domain.model.NPoint
import com.yondex.archivo.domain.model.Quad
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Result of a live detection, with the upright frame size used to map points onto the preview. */
data class LiveDetection(val quad: Quad?, val frameWidth: Int, val frameHeight: Int)

@Singleton
class EdgeDetector @Inject constructor() {

    fun detect(bitmap: Bitmap): Quad? {
        val small = BitmapIO.scaleDown(bitmap, DETECTION_SIZE)
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            detectGray(gray)
        } finally {
            rgba.release()
            gray.release()
            if (small !== bitmap) small.recycle()
        }
    }

    /** Detects a document in a YUV camera frame using only the luminance plane. */
    fun detect(image: ImageProxy): LiveDetection {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val rowStride = plane.rowStride
        val bytes = ByteArray(width * height)
        if (rowStride == width) {
            buffer.get(bytes, 0, bytes.size)
        } else {
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(bytes, row * width, width)
            }
        }

        val raw = Mat(height, width, CvType.CV_8UC1)
        val upright = Mat()
        return try {
            raw.put(0, 0, bytes)
            when (image.imageInfo.rotationDegrees) {
                90 -> Core.rotate(raw, upright, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(raw, upright, Core.ROTATE_180)
                270 -> Core.rotate(raw, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> raw.copyTo(upright)
            }
            val scaled = Mat()
            val scale = DETECTION_SIZE.toDouble() / max(upright.cols(), upright.rows())
            if (scale < 1.0) {
                Imgproc.resize(upright, scaled, Size(), scale, scale, Imgproc.INTER_AREA)
            } else {
                upright.copyTo(scaled)
            }
            val quad = try {
                detectGray(scaled)
            } finally {
                scaled.release()
            }
            LiveDetection(quad, upright.cols(), upright.rows())
        } finally {
            raw.release()
            upright.release()
        }
    }

    private fun detectGray(gray: Mat): Quad? {
        val enhanced = Mat()
        val blurred = Mat()
        // CLAHE helps laminated ID cards / KTP where glare flattens local contrast.
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(gray, enhanced)
        Imgproc.GaussianBlur(enhanced, blurred, Size(5.0, 5.0), 0.0)
        return try {
            val width = gray.cols()
            val height = gray.rows()
            var bestCorners: Array<Point>? = null
            var bestScore = 0.0
            // Strategies are tried in order; stop early once confidence is high enough.
            val strategies = listOf<(Mat) -> Mat>(
                { cannyEdges(it, 40.0, 120.0) },
                { cannyEdges(it, 25.0, 80.0) },
                { otsuMask(it) },
                { adaptiveMask(it) },
            )
            for (strategy in strategies) {
                val candidate = scoreContours(strategy(blurred), width, height)
                if (candidate != null && candidate.score > bestScore) {
                    bestScore = candidate.score
                    bestCorners = candidate.corners
                    if (bestScore >= EARLY_EXIT_SCORE) break
                }
            }
            val corners = bestCorners ?: return null
            Quad.fromUnordered(corners.map {
                NPoint((it.x / width).toFloat(), (it.y / height).toFloat())
            })
        } finally {
            enhanced.release()
            blurred.release()
        }
    }

    private fun cannyEdges(blurred: Mat, low: Double, high: Double): Mat {
        val edges = Mat()
        Imgproc.Canny(blurred, edges, low, high)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)
        kernel.release()
        return edges
    }

    private fun otsuMask(blurred: Mat): Mat {
        val mask = Mat()
        Imgproc.threshold(blurred, mask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
        return mask
    }

    private fun adaptiveMask(blurred: Mat): Mat {
        val mask = Mat()
        Imgproc.adaptiveThreshold(
            blurred, mask, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            31, 8.0,
        )
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
        return mask
    }

    private data class ScoredQuad(val corners: Array<Point>, val score: Double)

    /** Finds the best convex quadrilateral in a binary image and releases [binary]. */
    private fun scoreContours(binary: Mat, width: Int, height: Int): ScoredQuad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release()
        hierarchy.release()

        val frameArea = (width * height).toDouble()
        val minArea = frameArea * MIN_AREA_RATIO
        val maxArea = frameArea * MAX_AREA_RATIO
        var best: ScoredQuad? = null

        for (contour in contours) {
            val area = abs(Imgproc.contourArea(contour))
            if (area in minArea..maxArea) {
                val curve = MatOfPoint2f(*contour.toArray())
                val corners = approximateQuad(curve) ?: minAreaRectCorners(curve)
                if (corners != null) {
                    val polygon = MatOfPoint(*corners)
                    val areaMat = MatOfPoint2f(*corners)
                    val measuredArea = abs(Imgproc.contourArea(areaMat))
                    areaMat.release()
                    if (Imgproc.isContourConvex(polygon) && measuredArea >= minArea) {
                        val score = scoreQuad(corners, measuredArea, frameArea)
                        if (best == null || score > best.score) {
                            best = ScoredQuad(corners, score)
                        }
                    }
                    polygon.release()
                }
                curve.release()
            }
            contour.release()
        }
        return best
    }

    /**
     * Tries several approxPolyDP epsilons so rounded-corner cards (KTP/ID) still collapse to 4 points.
     */
    private fun approximateQuad(curve: MatOfPoint2f): Array<Point>? {
        val peri = Imgproc.arcLength(curve, true)
        for (epsilonFactor in APPROX_EPSILONS) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, approx, epsilonFactor * peri, true)
            val points = approx.toArray()
            approx.release()
            if (points.size == 4) return points
        }
        return null
    }

    /** Fallback for soft/rounded edges: use the oriented bounding rectangle. */
    private fun minAreaRectCorners(curve: MatOfPoint2f): Array<Point>? {
        val rect = Imgproc.minAreaRect(curve)
        val box = arrayOf(Point(), Point(), Point(), Point())
        rect.points(box)
        val w = hypot(box[0].x - box[1].x, box[0].y - box[1].y)
        val h = hypot(box[1].x - box[2].x, box[1].y - box[2].y)
        if (w < 1.0 || h < 1.0) return null
        val fill = abs(Imgproc.contourArea(curve)) / (w * h)
        // Reject loose boxes that don't hug the contour (noise / partial edges).
        if (fill < MIN_RECT_FILL) return null
        return box
    }

    /**
     * Prefers larger quads that look rectangular. ID-card aspect ratios (~1.4–1.8 landscape)
     * get a small boost so a nearby notebook corner doesn't win over a KTP.
     */
    private fun scoreQuad(corners: Array<Point>, area: Double, frameArea: Double): Double {
        val ordered = orderCorners(corners)
        val widthTop = distance(ordered[0], ordered[1])
        val widthBottom = distance(ordered[3], ordered[2])
        val heightLeft = distance(ordered[0], ordered[3])
        val heightRight = distance(ordered[1], ordered[2])
        val avgW = (widthTop + widthBottom) / 2.0
        val avgH = (heightLeft + heightRight) / 2.0
        if (avgW < 1.0 || avgH < 1.0) return 0.0

        val aspect = max(avgW, avgH) / min(avgW, avgH)
        // Paper ~√2≈1.41, ID cards ~1.58–1.75; reject extreme skinny shapes.
        if (aspect > MAX_ASPECT) return 0.0

        val rectangularity = min(widthTop, widthBottom) / max(widthTop, widthBottom) *
            min(heightLeft, heightRight) / max(heightLeft, heightRight)
        val areaScore = area / frameArea
        val idBoost = if (aspect in ID_ASPECT_MIN..ID_ASPECT_MAX) 1.15 else 1.0
        return areaScore * rectangularity * rectangularity * idBoost
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val list = points.toList()
        val tl = list.minBy { it.x + it.y }
        val br = list.maxBy { it.x + it.y }
        val tr = list.minBy { it.y - it.x }
        val bl = list.maxBy { it.y - it.x }
        return arrayOf(tl, tr, br, bl)
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private companion object {
        const val DETECTION_SIZE = 640
        /** Allow small cards (KTP/ID) that only fill ~4% of the frame. */
        const val MIN_AREA_RATIO = 0.04
        const val MAX_AREA_RATIO = 0.95
        const val MIN_RECT_FILL = 0.72
        const val MAX_ASPECT = 3.0
        const val ID_ASPECT_MIN = 1.35
        const val ID_ASPECT_MAX = 1.90
        /** Roughly a solid rectangular card covering ≥~12% of the frame. */
        const val EARLY_EXIT_SCORE = 0.08
        val APPROX_EPSILONS = doubleArrayOf(0.02, 0.03, 0.04, 0.055)
    }
}
