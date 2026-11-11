package com.yondex.archivo.scanner

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.yondex.archivo.domain.model.NPoint
import com.yondex.archivo.domain.model.Quad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Binds CameraX preview, live edge detection and still capture to a lifecycle. */
class DocumentCamera(
    private val context: Context,
    private val detector: EdgeDetector,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lastDetectionAt = 0L
    private var lastUiEmitAt = 0L
    private var stableAnchor: Quad? = null
    private var stableSince = 0L
    private var jumpCandidate: Quad? = null
    private var jumpCandidateCount = 0
    @Volatile private var capturedQuad: Quad? = null

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
        )
        .build()

    private val _detection = MutableStateFlow<LiveDetection?>(null)
    val detection: StateFlow<LiveDetection?> = _detection.asStateFlow()

    /** 0..1 progress of the document being held still; reaches 1 when it is ready for auto-capture. */
    private val _stability = MutableStateFlow(0f)
    val stability: StateFlow<Float> = _stability.asStateFlow()

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProvider = awaitCameraProvider()
        provider = cameraProvider

        val ratio43 = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(ratio43).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::analyze) }

        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
            imageCapture,
        )
        camera?.cameraControl?.enableTorch(_torchOn.value)
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching { future.get() }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(image: ImageProxy) {
        try {
            val result = detector.detect(image)
            val now = System.currentTimeMillis()
            val quad = result.quad
            if (quad != null) {
                lastDetectionAt = now
                val previous = _detection.value?.quad
                val smoothed = smoothQuad(previous, quad)
                // Drive stability from the displayed/smoothed quad so UI doesn't thrash on raw noise.
                updateStability(smoothed, now)
                publishDetection(result.copy(quad = smoothed), now)
            } else if (now - lastDetectionAt > LOST_TIMEOUT_MS) {
                jumpCandidate = null
                jumpCandidateCount = 0
                publishDetection(result, now, force = true)
                resetStability()
                capturedQuad = null
            }
            // Keep last good overlay briefly while a few frames miss — avoids border flicker.
        } catch (_: Exception) {
            _detection.value = null
            resetStability()
        } finally {
            image.close()
        }
    }

    private fun smoothQuad(previous: Quad?, next: Quad): Quad {
        if (previous == null) {
            jumpCandidate = null
            jumpCandidateCount = 0
            return next
        }
        val distance = previous.maxDistance(next)
        if (distance < JUMP_DISTANCE) {
            jumpCandidate = null
            jumpCandidateCount = 0
            return previous.lerp(next, SMOOTHING)
        }
        // Large jumps must win for several frames before we snap — stops strategy hopping.
        val candidate = jumpCandidate
        if (candidate != null && candidate.maxDistance(next) < JUMP_CONFIRM_DISTANCE) {
            jumpCandidateCount++
            jumpCandidate = candidate.lerp(next, 0.5f)
        } else {
            jumpCandidate = next
            jumpCandidateCount = 1
        }
        return if (jumpCandidateCount >= JUMP_CONFIRM_FRAMES) {
            val accepted = jumpCandidate ?: next
            jumpCandidate = null
            jumpCandidateCount = 0
            accepted
        } else {
            previous
        }
    }

    private fun publishDetection(result: LiveDetection, now: Long, force: Boolean = false) {
        if (!force && now - lastUiEmitAt < UI_EMIT_INTERVAL_MS) {
            val previous = _detection.value?.quad
            val next = result.quad
            if (previous != null && next != null && previous.maxDistance(next) < UI_EMIT_EPSILON) {
                return
            }
        }
        lastUiEmitAt = now
        _detection.value = result
    }

    private fun updateStability(quad: Quad, now: Long) {
        val captured = capturedQuad
        if (captured != null) {
            // Stay disarmed after a capture until the page is moved or replaced.
            if (captured.maxDistance(quad) < REARM_DISTANCE) {
                publishStability(0f)
                return
            }
            capturedQuad = null
        }
        val anchor = stableAnchor
        if (anchor == null || anchor.maxDistance(quad) > STABLE_DISTANCE) {
            // Hysteresis: only reset the lock timer on a meaningful move.
            stableAnchor = quad
            stableSince = now
        } else {
            // Slowly pull the anchor toward the live quad so drift doesn't accumulate.
            stableAnchor = anchor.lerp(quad, 0.08f)
        }
        val progress = ((now - stableSince).toFloat() / STABLE_DURATION_MS).coerceIn(0f, 1f)
        publishStability(progress)
    }

    private fun publishStability(value: Float) {
        val quantized = (value * STABILITY_STEPS).toInt() / STABILITY_STEPS
        if (quantized != _stability.value) {
            _stability.value = quantized
        }
    }

    private fun resetStability() {
        stableAnchor = null
        stableSince = 0L
        publishStability(0f)
    }

    /** Disarms auto-capture until the current page leaves the frame or moves noticeably. */
    fun onCaptured(quad: Quad?) {
        capturedQuad = quad ?: _detection.value?.quad
        resetStability()
    }

    fun toggleTorch() {
        val enabled = !_torchOn.value
        camera?.cameraControl?.enableTorch(enabled)
        _torchOn.value = enabled
    }

    /** Captures a full-resolution, upright bitmap. */
    suspend fun capture(): Bitmap = suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = runCatching {
                    val raw = image.toBitmap()
                    val upright = BitmapIO.rotate(raw, image.imageInfo.rotationDegrees)
                    if (upright !== raw) raw.recycle()
                    BitmapIO.scaleDown(upright, BitmapIO.MAX_STORED_DIMENSION)
                }
                image.close()
                result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        })
    }

    fun release() {
        provider?.unbindAll()
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
    }

    private companion object {
        const val LOST_TIMEOUT_MS = 1200L
        const val STABLE_DURATION_MS = 1100L
        const val STABLE_DISTANCE = 0.06f
        const val REARM_DISTANCE = 0.2f
        const val JUMP_DISTANCE = 0.08f
        const val JUMP_CONFIRM_DISTANCE = 0.1f
        const val JUMP_CONFIRM_FRAMES = 3
        const val SMOOTHING = 0.22f
        const val UI_EMIT_INTERVAL_MS = 66L
        const val UI_EMIT_EPSILON = 0.012f
        const val STABILITY_STEPS = 20f
    }
}

private fun Quad.maxDistance(other: Quad): Float =
    points.indices.maxOf { points[it].distanceTo(other.points[it]) }

private fun Quad.lerp(target: Quad, t: Float): Quad = Quad(points.indices.map {
    NPoint(
        points[it].x + (target.points[it].x - points[it].x) * t,
        points[it].y + (target.points[it].y - points[it].y) * t,
    )
})
