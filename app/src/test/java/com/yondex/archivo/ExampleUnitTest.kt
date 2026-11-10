package com.yondex.archivo

import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.domain.model.NPoint
import com.yondex.archivo.domain.model.Quad
import com.yondex.archivo.pdf.JpegPdfWriter
import com.yondex.archivo.pdf.PdfImagePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class QuadTest {
    @Test
    fun fromUnordered_ordersCornersClockwiseFromTopLeft() {
        val quad = Quad.fromUnordered(
            listOf(NPoint(0.9f, 0.85f), NPoint(0.1f, 0.1f), NPoint(0.15f, 0.9f), NPoint(0.8f, 0.05f))
        )
        assertEquals(NPoint(0.1f, 0.1f), quad.topLeft)
        assertEquals(NPoint(0.8f, 0.05f), quad.topRight)
        assertEquals(NPoint(0.9f, 0.85f), quad.bottomRight)
        assertEquals(NPoint(0.15f, 0.9f), quad.bottomLeft)
    }

    @Test
    fun encodeDecode_roundTrips() {
        val quad = Quad(listOf(NPoint(0.1f, 0.2f), NPoint(0.9f, 0.1f), NPoint(0.95f, 0.9f), NPoint(0.05f, 0.85f)))
        assertEquals(quad, Quad.decode(quad.encode()))
    }

    @Test
    fun decode_invalidValueFallsBackToFull() {
        assertEquals(Quad.FULL, Quad.decode("garbage"))
        assertEquals(Quad.FULL, Quad.decode(null))
    }

    @Test
    fun withPoint_clampsToImageBounds() {
        val moved = Quad.FULL.withPoint(0, NPoint(-0.5f, 1.5f))
        assertEquals(NPoint(0f, 1f), moved.topLeft)
    }
}

class SearchQueryTest {
    @Test
    fun buildMatchQuery_turnsWordsIntoPrefixTerms() {
        assertEquals("invoice* 2026*", DocumentRepository.buildMatchQuery("  Invoice, 2026! "))
    }

    @Test
    fun buildMatchQuery_neutralizesFtsSyntax() {
        assertEquals("a* or* b*", DocumentRepository.buildMatchQuery("\"a\" OR -b*"))
    }

    @Test
    fun buildMatchQuery_blankReturnsNull() {
        assertNull(DocumentRepository.buildMatchQuery("  !! "))
    }
}

class JpegPdfWriterTest {
    @Test
    fun write_producesValidXrefOffsets() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val output = ByteArrayOutputStream()
        JpegPdfWriter.write(output, "Dokumen Ünïcode", pageCount = 2) {
            PdfImagePage(595f, 842f, 100, 140, jpeg)
        }
        val bytes = output.toByteArray()
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Count 2"))

        val startXref = text.substringAfterLast("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.startsWith("xref", startXref))

        val entries = text.substring(startXref).lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertEquals(9, entries.size)
        entries.forEachIndexed { index, entry ->
            val offset = entry.substring(0, 10).toInt()
            assertTrue("object ${index + 1}", text.startsWith("${index + 1} 0 obj", offset))
        }
    }
}
