package com.yondex.archivo.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.scanner.DocumentCamera
import com.yondex.archivo.scanner.LiveDetection
import com.yondex.archivo.ui.components.Thumbnail
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onReview: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris -> viewModel.importImages(uris) }

    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val processing by viewModel.processingCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraContent(
                viewModel = viewModel,
                pageCount = pages.size,
                lastPagePath = pages.lastOrNull()?.processedPath,
                processing = processing > 0,
                onClose = onClose,
                onGallery = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onReview = onReview,
            )
        } else {
            PermissionRationale(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onGallery = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClose = onClose,
                onReview = onReview.takeIf { pages.isNotEmpty() },
            )
        }
    }
}

@Composable
private fun CameraContent(
    viewModel: ScannerViewModel,
    pageCount: Int,
    lastPagePath: String?,
    processing: Boolean,
    onClose: () -> Unit,
    onGallery: () -> Unit,
    onReview: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val camera = remember { DocumentCamera(context, viewModel.edgeDetector) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val detection by camera.detection.collectAsStateWithLifecycle()
    val stability by camera.stability.collectAsStateWithLifecycle()
    val torchOn by camera.torchOn.collectAsStateWithLifecycle()
    var capturing by remember { mutableStateOf(false) }
    var autoCapture by rememberSaveable { mutableStateOf(true) }
    val flash = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    fun capture() {
        if (capturing) return
        capturing = true
        val liveQuad = detection?.quad
        camera.onCaptured(liveQuad)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(350))
        }
        scope.launch {
            runCatching { camera.capture() }
                .onSuccess { viewModel.onCaptured(it, liveQuad) }
                .onFailure { viewModel.onCaptureFailed(it) }
            capturing = false
        }
    }

    LaunchedEffect(camera) {
        runCatching { camera.bind(lifecycleOwner, previewView) }
            .onFailure { viewModel.onCaptureFailed(it) }
    }
    DisposableEffect(camera) { onDispose { camera.release() } }
    LaunchedEffect(stability, autoCapture) {
        if (autoCapture && stability >= 1f && !capturing) capture()
    }

    val hint = when {
        detection?.quad == null -> "Arahkan kamera ke dokumen"
        autoCapture && stability > 0f -> "Tahan stabil…"
        autoCapture -> "Dokumen terdeteksi"
        else -> "Dokumen terdeteksi — tekan tombol"
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { autoCapture = !autoCapture }) {
                Text(
                    if (autoCapture) "AUTO" else "MANUAL",
                    color = if (autoCapture) MaterialTheme.colorScheme.primary else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(onClick = camera::toggleTorch) {
                Icon(
                    if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = Color.White,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            DetectionOverlay(detection, stability, Modifier.fillMaxSize())
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            ) {
                Text(
                    hint,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            IconButton(onClick = onGallery, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Impor dari galeri", tint = Color.White)
            }

            ShutterButton(
                busy = capturing,
                progress = if (autoCapture) stability else 0f,
                onClick = ::capture,
            )

            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (pageCount > 0 || processing) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            .clickable(enabled = pageCount > 0, onClick = onReview),
                        contentAlignment = Alignment.Center,
                    ) {
                        Thumbnail(lastPagePath, Modifier.fillMaxSize())
                        if (processing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    if (pageCount > 0) {
                        Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text("$pageCount") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(busy: Boolean, progress: Float, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .drawBehind {
                drawCircle(Color.White, style = Stroke(4.dp.toPx()))
                if (progress > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (busy) Color.Gray else Color.White)
            .clickable(enabled = !busy, onClick = onClick),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
    }
}

@Composable
private fun DetectionOverlay(detection: LiveDetection?, stability: Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanLine",
    )
    Canvas(modifier) {
        val quad = detection?.quad ?: return@Canvas
        val frameWidth = detection.frameWidth.toFloat()
        val frameHeight = detection.frameHeight.toFloat()
        val scale = min(size.width / frameWidth, size.height / frameHeight)
        val drawWidth = frameWidth * scale
        val drawHeight = frameHeight * scale
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        val points = quad.points.map { Offset(left + it.x * drawWidth, top + it.y * drawHeight) }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, color.copy(alpha = 0.15f + 0.2f * stability))
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))

        val (tl, tr, br, bl) = points
        val start = lerp(tl, bl, sweep)
        val end = lerp(tr, br, sweep)
        clipPath(path) {
            val glow = 28.dp.toPx()
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.45f), Color.Transparent),
                    startY = (start.y + end.y) / 2 - glow,
                    endY = (start.y + end.y) / 2 + glow,
                ),
                start = start,
                end = end,
                strokeWidth = glow * 2,
            )
            drawLine(color, start, end, strokeWidth = 2.dp.toPx())
        }
        points.forEach { drawCircle(Color.White, radius = 6.dp.toPx(), center = it) }
    }
}

@Composable
private fun PermissionRationale(
    onRequest: () -> Unit,
    onGallery: () -> Unit,
    onClose: () -> Unit,
    onReview: (() -> Unit)?,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Izin kamera diperlukan", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Archivo memerlukan akses kamera untuk memindai dokumen. Anda juga bisa mengimpor gambar dari galeri.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) { Text("Izinkan kamera") }
            TextButton(onClick = onGallery) { Text("Impor dari galeri") }
            if (onReview != null) TextButton(onClick = onReview) { Text("Lanjut ke tinjauan") }
            TextButton(onClick = onClose) { Text("Tutup") }
        }
    }
}
