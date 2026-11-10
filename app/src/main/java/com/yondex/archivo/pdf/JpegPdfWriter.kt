package com.yondex.archivo.pdf

import java.io.FilterOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfImagePage(
    val pageWidth: Float,
    val pageHeight: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val jpeg: ByteArray,
)

/**
 * Minimal PDF 1.4 writer where every page is a single JPEG image embedded with DCTDecode,
 * which keeps the output small. Pages are produced lazily so only one image is in memory.
 */
object JpegPdfWriter {

    fun write(output: OutputStream, title: String, pageCount: Int, pageAt: (Int) -> PdfImagePage) {
        require(pageCount > 0) { "PDF needs at least one page" }
        val out = CountingOutputStream(output.buffered())
        val objectCount = 3 + pageCount * 3
        val offsets = LongArray(objectCount + 1)

        fun pageObj(i: Int) = 4 + i * 3
        fun beginObject(number: Int) {
            offsets[number] = out.count
            out.ascii("$number 0 obj\n")
        }

        out.ascii("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        beginObject(1)
        out.ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        beginObject(2)
        val kids = (0 until pageCount).joinToString(" ") { "${pageObj(it)} 0 R" }
        out.ascii("<< /Type /Pages /Kids [$kids] /Count $pageCount >>\nendobj\n")

        beginObject(3)
        val date = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        out.ascii("<< /Title ${utf16Hex(title)} /Producer (Archivo) /CreationDate (D:$date) >>\nendobj\n")

        for (i in 0 until pageCount) {
            val page = pageAt(i)
            val pageNumber = pageObj(i)
            val imageNumber = pageNumber + 1
            val contentNumber = pageNumber + 2

            beginObject(pageNumber)
            out.ascii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${page.pageWidth.fmt()} ${page.pageHeight.fmt()}] " +
                    "/Resources << /XObject << /Im0 $imageNumber 0 R >> >> /Contents $contentNumber 0 R >>\nendobj\n"
            )

            beginObject(imageNumber)
            out.ascii(
                "<< /Type /XObject /Subtype /Image /Width ${page.imageWidth} /Height ${page.imageHeight} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n"
            )
            out.write(page.jpeg)
            out.ascii("\nendstream\nendobj\n")

            val scale = minOf(page.pageWidth / page.imageWidth, page.pageHeight / page.imageHeight)
            val drawWidth = page.imageWidth * scale
            val drawHeight = page.imageHeight * scale
            val x = (page.pageWidth - drawWidth) / 2f
            val y = (page.pageHeight - drawHeight) / 2f
            val content = "q ${drawWidth.fmt()} 0 0 ${drawHeight.fmt()} ${x.fmt()} ${y.fmt()} cm /Im0 Do Q"
            beginObject(contentNumber)
            out.ascii("<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")
        }

        val xrefOffset = out.count
        out.ascii("xref\n0 ${objectCount + 1}\n0000000000 65535 f \n")
        for (number in 1..objectCount) {
            out.ascii(String.format(Locale.US, "%010d 00000 n \n", offsets[number]))
        }
        out.ascii("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.flush()
    }

    private fun utf16Hex(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_16BE)
        return buildString {
            append("<FEFF")
            bytes.forEach { append(String.format(Locale.US, "%02X", it.toInt() and 0xFF)) }
            append(">")
        }
    }

    private fun Float.fmt(): String = String.format(Locale.US, "%.2f", this)

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        fun ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))
    }
}
