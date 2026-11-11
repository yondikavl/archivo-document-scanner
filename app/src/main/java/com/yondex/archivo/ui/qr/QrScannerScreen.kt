package com.yondex.archivo.ui.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.ContentCopy
import androidx.compose.material.icons.sharp.FlashOff
import androidx.compose.material.icons.sharp.FlashOn
import androidx.compose.material.icons.sharp.Link
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.yondex.archivo.R
import com.yondex.archivo.ui.components.SketchFilledButton
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchOutlinedButton
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.sketchBorder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasPermission = it }

    var result by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val locked = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                )
                .build(),
        )
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(hasPermission, result) {
        if (!hasPermission) return@LaunchedEffect
        if (result != null) {
            cameraController.clearImageAnalysisAnalyzer()
            return@LaunchedEffect
        }
        locked.set(false)
        cameraController.setImageAnalysisAnalyzer(
            analysisExecutor,
            MlKitAnalyzer(
                listOf(barcodeScanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                analysisExecutor,
            ) { mlResult ->
                if (locked.get()) return@MlKitAnalyzer
                val codes = mlResult?.getValue(barcodeScanner).orEmpty()
                val raw = codes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() } ?: return@MlKitAnalyzer
                if (locked.compareAndSet(false, true)) {
                    ContextCompat.getMainExecutor(context).execute {
                        result = raw
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            },
        )
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    LaunchedEffect(torchOn, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        runCatching { cameraController.enableTorch(torchOn) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !hasPermission -> PermissionPane(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            controller = cameraController
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ScanFrame(modifier = Modifier.align(Alignment.Center))
                TopBar(
                    torchOn = torchOn,
                    onToggleTorch = { torchOn = !torchOn },
                    onClose = onClose,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                )
                if (result == null) {
                    Text(
                        stringResource(R.string.qr_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(24.dp),
                    )
                }
            }
        }

        result?.let { value ->
            ResultSheet(
                value = value,
                onCopy = {
                    copyText(context, value)
                    Toast.makeText(context, context.getString(R.string.qr_copied), Toast.LENGTH_SHORT).show()
                },
                onShare = { shareText(context, value) },
                onOpen = { openUrl(context, value) },
                onScanAgain = { result = null },
                onClose = onClose,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TopBar(
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Sharp.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
        }
        Text(
            stringResource(R.string.qr_scanner_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleTorch) {
            Icon(
                if (torchOn) Icons.Sharp.FlashOn else Icons.Sharp.FlashOff,
                contentDescription = stringResource(R.string.flash),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(240.dp)
            .border(SketchStroke, Color.White, RectangleShape),
    )
}

@Composable
private fun ResultSheet(
    value: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onScanAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUrl = remember(value) { looksLikeUrl(value) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        shape = RectangleShape,
        modifier = modifier
            .sketchBorder(RectangleShape, SketchInk, SketchStroke)
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_qrcode),
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.qr_result_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Sharp.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SketchOutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f), fillWidth = true) {
                    Icon(Icons.Sharp.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy))
                }
                SketchOutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), fillWidth = true) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
            if (isUrl) {
                Spacer(Modifier.height(10.dp))
                SketchFilledButton(onClick = onOpen, modifier = Modifier.fillMaxWidth(), fillWidth = true) {
                    Icon(Icons.Sharp.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_open))
                }
            }
            Spacer(Modifier.height(10.dp))
            SketchFilledButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true,
            ) {
                Text(stringResource(R.string.qr_scan_again))
            }
        }
    }
}

@Composable
private fun PermissionPane(onRequest: () -> Unit, onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_qrcode),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.camera_permission_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.qr_permission_message),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        SketchFilledButton(onClick = onRequest) {
            Text(stringResource(R.string.allow_camera))
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.action_close), color = Color.White)
        }
    }
}

private fun looksLikeUrl(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("www.", ignoreCase = true)
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("qr", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
}

private fun openUrl(context: Context, value: String) {
    val url = when {
        value.startsWith("http://", true) || value.startsWith("https://", true) -> value
        value.startsWith("www.", true) -> "https://$value"
        else -> return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.qr_open_failed), Toast.LENGTH_SHORT).show()
    }
}
