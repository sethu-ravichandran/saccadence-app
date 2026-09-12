package com.arra.saccadence.pairing

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.net.URI
import java.util.concurrent.Executors

/** Parsed out of the rig's pairing QR, e.g. `ws://192.168.1.42:8765/?join=ABC123` — see saccadence-rig's startScreen.js. */
data class ScannedRigAddress(val host: String, val port: Int, val sessionCode: String)

/** Parses the rig's join URL. Returns null for anything else so a stray QR code in frame is silently ignored. */
internal fun parseRigJoinUrl(raw: String): ScannedRigAddress? = runCatching {
    val uri = URI(raw)
    if (uri.scheme != "ws" && uri.scheme != "wss") return null
    val host = uri.host ?: return null
    val port = if (uri.port != -1) uri.port else 8765
    val sessionCode = uri.query
        ?.split("&")
        ?.firstNotNullOfOrNull { param ->
            val (key, value) = param.split("=", limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
            if (key == "join") value else null
        }
        ?: return null
    ScannedRigAddress(host, port, sessionCode)
}.getOrNull()

/**
 * Live camera view that decodes the rig's pairing QR as soon as it's in frame.
 * Calls [onScanned] at most once — the caller is expected to stop compositing
 * this (or navigate away) on the first hit rather than rely on this to debounce.
 * [onScanned] also receives a snapshot of the preview at the moment of the hit
 * (null if the capture failed) so the caller can freeze on that frame instead
 * of just the decoded text.
 */
@Composable
fun QrScannerFeed(onScanned: (ScannedRigAddress, Bitmap?) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx -> buildQrScannerView(ctx, lifecycleOwner, executor, onScanned) },
    )
}

@OptIn(ExperimentalGetImage::class)
private fun buildQrScannerView(
    ctx: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    executor: java.util.concurrent.ExecutorService,
    onScanned: (ScannedRigAddress, Bitmap?) -> Unit,
): PreviewView {
    val previewView = PreviewView(ctx).apply {
        // COMPATIBLE (TextureView-backed) rather than the SurfaceView-backed default:
        // this view sits directly above other Compose content (status text, buttons)
        // in a single Column, and on some OEM skins (seen on iQOO/vivo) a SurfaceView
        // there renders a frame behind, ignoring normal view clipping/z-order until the
        // camera session settles — visible as a black box bleeding over its neighbors.
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
    val mainExecutor = ContextCompat.getMainExecutor(ctx)
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    var delivered = false

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (delivered || mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            if (delivered) return@addOnSuccessListener
                            val address = barcodes.firstNotNullOfOrNull { it.rawValue?.let(::parseRigJoinUrl) }
                            if (address != null) {
                                delivered = true
                                mainExecutor.execute { onScanned(address, previewView.bitmap) }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
    }, mainExecutor)

    return previewView
}
