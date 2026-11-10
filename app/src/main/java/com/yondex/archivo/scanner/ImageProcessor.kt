package com.yondex.archivo.scanner

import android.graphics.Bitmap
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.domain.model.Quad
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class ImageProcessor @Inject constructor() {

    /** Reads [original], applies crop, filter and rotation, and writes the result as JPEG to [output]. */
    fun process(original: File, quad: Quad, rotation: Int, filter: PageFilter, output: File) {
        val source = BitmapIO.decodeFile(original, BitmapIO.MAX_STORED_DIMENSION)
        val result = process(source, quad, rotation, filter)
        BitmapIO.saveJpeg(result, output)
        if (result !== source) result.recycle()
        source.recycle()
    }

    fun process(source: Bitmap, quad: Quad, rotation: Int, filter: PageFilter): Bitmap {
        var mat = Mat()
        Utils.bitmapToMat(source, mat)
        try {
            if (!quad.isFull) mat = mat.replaceWith(warp(mat, quad))
            mat = mat.replaceWith(applyFilter(mat, filter))
            mat = mat.replaceWith(rotate(mat, rotation))
            val output = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, output)
            return output
        } finally {
            mat.release()
        }
    }

    private fun warp(rgba: Mat, quad: Quad): Mat {
        val w = rgba.cols().toDouble()
        val h = rgba.rows().toDouble()
        val corners = quad.points.map { Point(it.x * w, it.y * h) }
        val (tl, tr, br, bl) = corners

        val targetWidth = max(distance(tl, tr), distance(bl, br)).roundToInt().coerceAtLeast(1)
        val targetHeight = max(distance(tl, bl), distance(tr, br)).roundToInt().coerceAtLeast(1)

        val src = MatOfPoint2f(tl, tr, br, bl)
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetWidth - 1.0, 0.0),
            Point(targetWidth - 1.0, targetHeight - 1.0),
            Point(0.0, targetHeight - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        Imgproc.warpPerspective(
            rgba, warped, transform, Size(targetWidth.toDouble(), targetHeight.toDouble()),
            Imgproc.INTER_CUBIC,
        )
        src.release()
        dst.release()
        transform.release()
        return warped
    }

    private fun applyFilter(rgba: Mat, filter: PageFilter): Mat {
        val out = Mat()
        when (filter) {
            PageFilter.ORIGINAL -> rgba.copyTo(out)
            PageFilter.ENHANCE -> {
                val rgb = Mat()
                Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
                val channels = ArrayList<Mat>()
                Core.split(rgb, channels)
                val flattened = channels.map { channel -> flattenIllumination(channel).also { channel.release() } }
                Core.merge(flattened, rgb)
                flattened.forEach { it.release() }
                sharpen(rgb)
                Imgproc.cvtColor(rgb, out, Imgproc.COLOR_RGB2RGBA)
                rgb.release()
            }
            PageFilter.GRAYSCALE -> {
                val gray = flattenedGray(rgba)
                sharpen(gray)
                Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGBA)
                gray.release()
            }
            PageFilter.BLACK_WHITE -> {
                val gray = flattenedGray(rgba)
                val blockSize = (max(gray.cols(), gray.rows()) / 30).let { if (it % 2 == 0) it + 1 else it }
                    .coerceAtLeast(15)
                Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
                Imgproc.adaptiveThreshold(
                    gray, gray, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                    blockSize, 15.0,
                )
                Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGBA)
                gray.release()
            }
        }
        return out
    }

    private fun flattenedGray(rgba: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        return flattenIllumination(gray).also { gray.release() }
    }

    /**
     * Estimates the paper background (text removed by dilation + median blur on a downscaled copy)
     * and divides it out, so shadows and uneven lighting become a flat white page.
     */
    private fun flattenIllumination(channel: Mat): Mat {
        val scale = BACKGROUND_SIZE.toDouble() / max(channel.cols(), channel.rows())
        val small = Mat()
        Imgproc.resize(channel, small, Size(), scale.coerceAtMost(1.0), scale.coerceAtMost(1.0), Imgproc.INTER_AREA)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        Imgproc.dilate(small, small, kernel)
        Imgproc.medianBlur(small, small, 21)
        val background = Mat()
        Imgproc.resize(small, background, channel.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)

        val result = Mat()
        Core.divide(channel, background, result, 255.0)
        // Stretch so faint ink gets darker while the page stays white.
        result.convertTo(result, -1, 1.15, -25.0)

        kernel.release()
        small.release()
        background.release()
        return result
    }

    private fun sharpen(image: Mat) {
        val blurred = Mat()
        Imgproc.GaussianBlur(image, blurred, Size(0.0, 0.0), 1.5)
        Core.addWeighted(image, 1.6, blurred, -0.6, 0.0, image)
        blurred.release()
    }

    private fun rotate(rgba: Mat, rotation: Int): Mat {
        val out = Mat()
        when (((rotation % 360) + 360) % 360) {
            90 -> Core.rotate(rgba, out, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(rgba, out, Core.ROTATE_180)
            270 -> Core.rotate(rgba, out, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> rgba.copyTo(out)
        }
        return out
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun Mat.replaceWith(next: Mat): Mat {
        if (next !== this) release()
        return next
    }

    private companion object {
        const val BACKGROUND_SIZE = 800
    }
}
