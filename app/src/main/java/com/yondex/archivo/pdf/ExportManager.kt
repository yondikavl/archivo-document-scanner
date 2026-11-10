package com.yondex.archivo.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.yondex.archivo.data.file.FileStorage
import com.yondex.archivo.data.file.toSafeFileName
import com.yondex.archivo.domain.model.Document
import com.yondex.archivo.domain.model.ExportQuality
import com.yondex.archivo.domain.model.Page
import com.yondex.archivo.domain.model.PdfPageSize
import com.yondex.archivo.scanner.BitmapIO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ImageFormat(val extension: String, val mimeType: String, val compressFormat: Bitmap.CompressFormat) {
    JPG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", "image/png", Bitmap.CompressFormat.PNG),
}

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FileStorage,
) {
    suspend fun writePdf(
        document: Document,
        pages: List<Page>,
        pageSize: PdfPageSize,
        quality: ExportQuality,
        output: OutputStream,
    ) = withContext(Dispatchers.IO) {
        JpegPdfWriter.write(output, document.title, pages.size) { index ->
            val bitmap = BitmapIO.decodeFile(File(pages[index].processedPath), quality.maxDimension)
            val jpeg = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, stream)
                stream.toByteArray()
            }
            val (pageWidth, pageHeight) = pageSizeFor(pageSize, bitmap.width, bitmap.height)
            PdfImagePage(pageWidth, pageHeight, bitmap.width, bitmap.height, jpeg).also { bitmap.recycle() }
        }
    }

    suspend fun createPdfFile(
        document: Document,
        pages: List<Page>,
        pageSize: PdfPageSize,
        quality: ExportQuality,
    ): File = withContext(Dispatchers.IO) {
        val file = File(storage.exportDir, "${document.title.toSafeFileName()}.pdf")
        file.outputStream().use { writePdf(document, pages, pageSize, quality, it) }
        file
    }

    suspend fun createImageFiles(document: Document, pages: List<Page>, format: ImageFormat): List<File> =
        withContext(Dispatchers.IO) {
            val baseName = document.title.toSafeFileName()
            pages.mapIndexed { index, page ->
                val file = File(storage.exportDir, "${baseName}_${index + 1}.${format.extension}")
                writeImage(page, format, file.outputStream())
                file
            }
        }

    suspend fun createTextFile(document: Document, pages: List<Page>): File = withContext(Dispatchers.IO) {
        val file = File(storage.exportDir, "${document.title.toSafeFileName()}.txt")
        file.writeText(buildText(pages))
        file
    }

    suspend fun writeText(pages: List<Page>, output: OutputStream) = withContext(Dispatchers.IO) {
        output.bufferedWriter().apply { write(buildText(pages)); flush() }
    }

    suspend fun saveToUri(uri: Uri, block: suspend (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open $uri")
        stream.use { block(it) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveImagesToGallery(document: Document, pages: List<Page>, format: ImageFormat): Int =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val baseName = document.title.toSafeFileName()
            pages.forEachIndexed { index, page ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${baseName}_${index + 1}.${format.extension}")
                    put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Archivo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Cannot create gallery entry")
                resolver.openOutputStream(uri)?.let { writeImage(page, format, it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            pages.size
        }

    fun shareIntent(files: List<File>, mimeType: String, title: String): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = mimeType
        send.putExtra(Intent.EXTRA_SUBJECT, title)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
    }

    fun shareTextIntent(text: String, title: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            title,
        )

    private fun writeImage(page: Page, format: ImageFormat, output: OutputStream) {
        output.use { stream ->
            if (format == ImageFormat.JPG) {
                File(page.processedPath).inputStream().use { it.copyTo(stream) }
            } else {
                val bitmap = BitmapIO.decodeFile(File(page.processedPath))
                bitmap.compress(format.compressFormat, 100, stream)
                bitmap.recycle()
            }
        }
    }

    private fun buildText(pages: List<Page>): String = pages.mapIndexed { index, page ->
        "--- Halaman ${index + 1} ---\n${page.ocrText.orEmpty()}"
    }.joinToString("\n\n")

    private fun pageSizeFor(size: PdfPageSize, width: Int, height: Int): Pair<Float, Float> {
        val landscape = width > height
        return when (size) {
            PdfPageSize.A4 -> if (landscape) 841.89f to 595.28f else 595.28f to 841.89f
            PdfPageSize.LETTER -> if (landscape) 792f to 612f else 612f to 792f
            PdfPageSize.FIT_IMAGE -> {
                val pageWidth = if (landscape) 841.89f else 595.28f
                pageWidth to pageWidth * height / width
            }
        }
    }
}
