package com.yondex.archivo.ocr

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yondex.archivo.data.local.db.PageDao
import com.yondex.archivo.data.repository.DocumentRepository
import com.yondex.archivo.domain.model.OcrStatus
import com.yondex.archivo.scanner.BitmapIO
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pageDao: PageDao,
    private val documentRepository: DocumentRepository,
    private val textRecognizer: TextRecognizer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pageDao.replaceOcrStatus(from = OcrStatus.RUNNING, to = OcrStatus.PENDING)
        while (true) {
            val batch = pageDao.getByStatus(OcrStatus.PENDING, BATCH_SIZE)
            if (batch.isEmpty()) break
            val touchedDocuments = mutableSetOf<Long>()
            for (page in batch) {
                if (isStopped) return Result.retry()
                pageDao.setOcrStatus(page.id, OcrStatus.RUNNING)
                try {
                    val bitmap = BitmapIO.decodeFile(File(page.processedPath), OCR_MAX_DIMENSION)
                    val text = try {
                        textRecognizer.recognize(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    pageDao.setOcrResult(page.id, text, OcrStatus.DONE)
                } catch (e: Exception) {
                    Log.w(TAG, "OCR failed for page ${page.id}", e)
                    pageDao.setOcrResult(page.id, page.ocrText, OcrStatus.FAILED)
                }
                touchedDocuments += page.documentId
            }
            touchedDocuments.forEach { documentRepository.refreshSearchIndex(it) }
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "OcrWorker"
        const val BATCH_SIZE = 10
        const val OCR_MAX_DIMENSION = 2400
    }
}
