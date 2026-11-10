package com.yondex.archivo.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextRecognizer @Inject constructor() {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): String {
        val result = client.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks.joinToString("\n\n") { it.text }.trim()
    }
}
