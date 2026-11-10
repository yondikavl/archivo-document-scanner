package com.yondex.archivo.ocr

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<OcrWorker>().build(),
        )
    }

    private companion object {
        const val WORK_NAME = "ocr"
    }
}
