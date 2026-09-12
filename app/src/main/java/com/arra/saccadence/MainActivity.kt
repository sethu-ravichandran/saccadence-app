package com.arra.saccadence

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arra.saccadence.marker.ImageProxyFrameSampler
import com.arra.saccadence.marker.MarkerDecoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Nothing but a raw back-camera preview. No marker decode, no MediaPipe,
 * no analysis — that comes after this pipeline is proven on real hardware.
 */
class MainActivity : ComponentActivity() {

    private val cameraGranted = mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted.value = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!cameraGranted.value) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val granted by cameraGranted
                    if (granted) {
                        CameraPreview()
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text("Camera permission required")
                        }
                    }
                }
            }
        }
    }
}

private const val TAG = "SaccadenceAnalyzer"

/**
 * Logs actual resolution + measured fps off the ImageAnalysis stream itself —
 * this is what the sensor is really delivering through the standard
 * (non-high-speed) session, not the 120/240fps high-speed-session numbers
 * confirmed separately via CameraCharacteristics. Those two numbers can
 * legitimately differ; this stub tells us which one a plain analyzer gets.
 */
private class FrameRateLogger : ImageAnalysis.Analyzer {
    private var windowStartNs = 0L
    private var framesInWindow = 0
    private var loggedResolution = false
    private var lastLoggedFrameId: Int? = null

    private val markerDecoder = MarkerDecoder()

    override fun analyze(image: ImageProxy) {
        if (!loggedResolution) {
            Log.i(TAG, "ImageAnalysis resolution: ${image.width}x${image.height}, format=${image.format}")
            loggedResolution = true
        }

        val now = System.nanoTime()
        if (windowStartNs == 0L) {
            windowStartNs = now
        }
        framesInWindow++

        val elapsedNs = now - windowStartNs
        if (elapsedNs >= 1_000_000_000L) {
            val measuredFps = framesInWindow / (elapsedNs / 1_000_000_000.0)
            Log.i(TAG, "Measured fps over last window: ${(measuredFps * 10).roundToInt() / 10.0}")
            windowStartNs = now
            framesInWindow = 0
        }

        val result = markerDecoder.decode(ImageProxyFrameSampler(image))
        if (result != null && result.frameId != lastLoggedFrameId) {
            lastLoggedFrameId = result.frameId
            Log.i(
                TAG,
                "Marker: active=${result.active} frameId=${result.frameId} " +
                    "guardBox=${result.guardBox.left},${result.guardBox.top}," +
                    "${result.guardBox.right},${result.guardBox.bottom}"
            )
        }

        image.close()
    }
}

@Composable
fun CameraPreview() {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val analysisExecutor = Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, FrameRateLogger())
                    }

                // Back camera, per the fps investigation — front caps at 30fps,
                // back exposes real high-speed modes (1080p up to 240fps) in a
                // constrained high-speed session, which this stub is NOT using.
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
