package com.yondex.archivo.pdf

import android.graphics.Bitmap
import com.yondex.archivo.scanner.BitmapIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class PdfCompressResult(
    val bytes: Long,
    val pageCount: Int,
    val maxDimension: Int,
    val jpegQuality: Int,
    val metTarget: Boolean,
)

/**
 * Rasterizes a PDF and re-encodes pages as JPEG images, stepping down resolution/quality
 * until the file is at or under [targetBytes] (or the smallest achievable size).
 */
object PdfCompressor {
    const val TARGET_100KB = 100L * 1024

    private val LEVELS = listOf(
        1600 to 72,
        1280 to 65,
        1024 to 58,
        800 to 50,
        640 to 42,
        520 to 36,
        420 to 30,
        340 to 24,
        280 to 20,
        220 to 16,
    )

    suspend fun compress(
        source: File,
        title: String,
        output: File,
        targetBytes: Long = TARGET_100KB,
    ): PdfCompressResult = withContext(Dispatchers.IO) {
        require(source.exists()) { "PDF not found" }
        val pageCount = PdfRasterizer.pageCount(source)
        require(pageCount > 0) { "PDF has no pages" }

        output.parentFile?.mkdirs()
        var bestFile: File? = null
        var bestBytes = Long.MAX_VALUE
        var bestDim = LEVELS.first().first
        var bestQuality = LEVELS.first().second

        try {
            for ((dim, quality) in LEVELS) {
                val attempt = File(output.parentFile, "compress_${dim}_${quality}_${System.nanoTime()}.pdf")
                writeAttempt(source, title, pageCount, dim, quality, attempt)
                val size = attempt.length()
                if (size < bestBytes) {
                    bestFile?.delete()
                    bestFile = attempt
                    bestBytes = size
                    bestDim = dim
                    bestQuality = quality
                } else {
                    attempt.delete()
                }
                if (size <= targetBytes) break
            }

            val chosen = bestFile ?: error("Compression failed")
            if (chosen.absolutePath != output.absolutePath) {
                output.delete()
                if (!chosen.renameTo(output)) {
                    chosen.copyTo(output, overwrite = true)
                    chosen.delete()
                }
            }
            PdfCompressResult(
                bytes = output.length(),
                pageCount = pageCount,
                maxDimension = bestDim,
                jpegQuality = bestQuality,
                metTarget = output.length() <= targetBytes,
            )
        } finally {
            bestFile?.takeIf { it.exists() && it.absolutePath != output.absolutePath }?.delete()
        }
    }

    private suspend fun writeAttempt(
        source: File,
        title: String,
        pageCount: Int,
        maxDimension: Int,
        jpegQuality: Int,
        output: File,
    ) {
        val pages = ArrayList<PdfImagePage>(pageCount)
        try {
            for (index in 0 until pageCount) {
                val bitmap = PdfRasterizer.renderPage(source, index, maxDimension)
                try {
                    val scaled = BitmapIO.scaleDown(bitmap, maxDimension)
                    try {
                        val jpeg = ByteArrayOutputStream().use { stream ->
                            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                            stream.toByteArray()
                        }
                        pages += PdfImagePage(
                            pageWidth = scaled.width.toFloat(),
                            pageHeight = scaled.height.toFloat(),
                            imageWidth = scaled.width,
                            imageHeight = scaled.height,
                            jpeg = jpeg,
                        )
                    } finally {
                        if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            output.outputStream().use { stream ->
                JpegPdfWriter.write(stream, title, pages.size) { pages[it] }
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }
}
