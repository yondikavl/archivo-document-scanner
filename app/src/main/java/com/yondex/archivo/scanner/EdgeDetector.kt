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
import kotlin.math.max

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
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        return try {
            findQuad(cannyEdges(blurred), gray.cols(), gray.rows())
                ?: findQuad(otsuMask(blurred), gray.cols(), gray.rows())
        } finally {
            blurred.release()
        }
    }

    private fun cannyEdges(blurred: Mat): Mat {
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.dilate(edges, edges, kernel)
        kernel.release()
        return edges
    }

    private fun otsuMask(blurred: Mat): Mat {
        val mask = Mat()
        Imgproc.threshold(blurred, mask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
        return mask
    }

    /** Finds the largest convex quadrilateral in a binary image and releases [binary]. */
    private fun findQuad(binary: Mat, width: Int, height: Int): Quad? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release()
        hierarchy.release()

        val minArea = width * height * MIN_AREA_RATIO
        var best: Array<Point>? = null
        var bestArea = 0.0
        for (contour in contours) {
            if (Imgproc.contourArea(contour) >= minArea) {
                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, 0.02 * Imgproc.arcLength(curve, true), true)
                if (approx.total() == 4L) {
                    val polygon = MatOfPoint(*approx.toArray())
                    val area = abs(Imgproc.contourArea(approx))
                    if (Imgproc.isContourConvex(polygon) && area > bestArea) {
                        bestArea = area
                        best = approx.toArray()
                    }
                    polygon.release()
                }
                curve.release()
                approx.release()
            }
            contour.release()
        }

        val corners = best ?: return null
        return Quad.fromUnordered(corners.map {
            NPoint((it.x / width).toFloat(), (it.y / height).toFloat())
        })
    }

    private companion object {
        const val DETECTION_SIZE = 640
        const val MIN_AREA_RATIO = 0.12
    }
}
