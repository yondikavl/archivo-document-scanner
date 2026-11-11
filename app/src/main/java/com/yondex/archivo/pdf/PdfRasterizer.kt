package com.yondex.archivo.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.yondex.archivo.scanner.BitmapIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Renders PDF pages one at a time for merge/split without keeping the whole file in memory. */
object PdfRasterizer {

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        open(file) { it.pageCount }
    }

    suspend fun renderPage(
        file: File,
        index: Int,
        maxDimension: Int = BitmapIO.MAX_STORED_DIMENSION,
    ): Bitmap = withContext(Dispatchers.IO) {
        open(file) { renderer ->
            require(index in 0 until renderer.pageCount) { "Page $index out of range" }
            val page = renderer.openPage(index)
            try {
                val longest = max(page.width, page.height).coerceAtLeast(1)
                val scale = if (maxDimension > 0 && longest > maxDimension / 2) {
                    (maxDimension / 2f) / longest
                } else {
                    2f
                }
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                BitmapIO.scaleDown(bitmap, maxDimension).also { if (it !== bitmap) bitmap.recycle() }
            } finally {
                page.close()
            }
        }
    }

    suspend fun writeJpegPage(
        file: File,
        index: Int,
        target: File,
        maxDimension: Int = BitmapIO.MAX_STORED_DIMENSION,
        quality: Int = 92,
    ): File = withContext(Dispatchers.IO) {
        val bitmap = renderPage(file, index, maxDimension)
        try {
            BitmapIO.saveJpeg(bitmap, target, quality)
            target
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private inline fun <T> open(file: File, block: (PdfRenderer) -> T): T {
        require(file.exists()) { "PDF not found" }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        return try {
            block(renderer)
        } finally {
            renderer.close()
            descriptor.close()
        }
    }
}
