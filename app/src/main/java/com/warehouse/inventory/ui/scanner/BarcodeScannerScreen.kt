package com.warehouse.inventory.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.common.AppTopBar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Full-screen camera barcode/QR scanner.
 *
 * Reused from three places — the dashboard (scan to open a product), stock-in and stock-out
 * (scan to select the product) — so it knows nothing about what the code means. It reports
 * the raw string through [onCodeScanned] and lets the caller decide.
 *
 * [statusMessage] lets the caller show feedback without leaving the camera, which is what
 * makes "unknown barcode" recoverable: the user just scans the next label.
 */
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onCodeScanned: (String) -> Unit,
    statusMessage: String? = null,
    /** Incremented by the caller to re-arm the scanner after handling a result. */
    resetToken: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.scan_barcode), onBack = onBack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasPermission) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onCodeScanned = onCodeScanned,
                    resetToken = resetToken,
                    statusMessage = statusMessage
                )
            } else {
                PermissionRationale(
                    permanentlyDenied = permissionRequested,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCodeScanned: (String) -> Unit,
    resetToken: Int,
    statusMessage: String?
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    // rememberUpdatedState so the long-lived analyzer always calls the current lambda
    // instead of capturing the one from the composition that created it.
    val currentOnCode by rememberUpdatedState(onCodeScanned)
    val analyzer = remember { BarcodeAnalyzer { code -> currentOnCode(code) } }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    // Re-arm after the caller reports it has handled a scan.
    LaunchedEffect(resetToken) {
        if (resetToken > 0) analyzer.reset()
    }

    DisposableEffect(lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                // Only the newest frame matters for scanning; queuing them adds latency.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }

            runCatching {
                provider?.unbindAll()
                camera = provider?.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
            analyzer.release()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Aiming frame — purely an affordance; ML Kit scans the whole frame.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = stringResource(R.string.scanner_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Torch: warehouse aisles are usually badly lit, and label contrast suffers.
        val torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true
        if (torchAvailable) {
            IconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                val lit = camera?.cameraInfo?.torchState?.value == TorchState.ON || torchOn
                Icon(
                    imageVector = if (lit) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = stringResource(R.string.scanner_torch),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.camera_permission_denied_hint
                else R.string.camera_permission_rationale
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}
