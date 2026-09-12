package com.arra.saccadence.calibration

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arra.saccadence.marker.ImageProxyFrameSampler
import com.arra.saccadence.marker.MarkerDecoder
import java.util.concurrent.Executors
import kotlin.math.sqrt

/** How many recent guard positions we judge lock stability from. */
private const val WINDOW_SIZE = 8

/** Max-minus-min spread, in pixels, allowed within the window to call it locked. */
private const val LOCK_JITTER_THRESHOLD_PX = 6f

/** If nothing's been seen this recently, we've lost the marker, not just jittering. */
private const val STALE_AFTER_MS = 1500L

private data class CalibrationState(
    val statusText: String = "Point the camera at the rig screen's marker corner.",
    val locked: Boolean = false,
    val jitterPx: Float? = null,
    val sampleCount: Int = 0,
)

/**
 * The one-time session-start marker check (rig's SETUP_CALIBRATION -> READY
 * gate, `role: "setup"` in the wire protocol). Operator-paced on the rig
 * side (ended by a keypress, not a fixed timer), so unlike a trial's
 * pre/post calibration bracket there is no bounded window here to compute a
 * clock offset from — this screen only confirms framing: can the phone see
 * and stably decode the marker at all. The real, per-trial clock offset,
 * jitter, and drift numbers come from the fixed 5-second pre/post brackets
 * around each trial (see [TrialSessionController]) and are reported on the
 * results screen, not here.
 */
@Composable
fun CalibrationScreen(onStartTest: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var calibration by remember { mutableStateOf(CalibrationState()) }
    var retryTick by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "Calibration", style = MaterialTheme.typography.headlineMedium)
        Spacer(6.dp)
        Text(
            text = "Confirm the phone can see the rig's marker before the first trial.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(16.dp)

        Box(
            modifier = Modifier.fillMaxWidth().height(320.dp).background(Color.Black, RoundedCornerShape(12.dp))
        ) {
            key(retryTick) {
                CalibrationCameraFeed(lifecycleOwner = lifecycleOwner, onState = { calibration = it })
            }
        }

        Spacer(20.dp)

        val statusColor = when {
            calibration.locked -> Color(0xFF1E6A4C)
            calibration.sampleCount > 0 -> Color(0xFF8A5000)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(text = calibration.statusText, style = MaterialTheme.typography.titleMedium, color = statusColor)

        Spacer(12.dp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledStat(label = "Frames locked", value = calibration.sampleCount.toString())
            LabeledStat(label = "Jitter", value = calibration.jitterPx?.let { "${it.toInt()} px" } ?: "—")
        }

        Spacer(24.dp)

        Button(onClick = onStartTest, enabled = calibration.locked, modifier = Modifier.fillMaxWidth()) {
            Text("Start test")
        }

        Spacer(12.dp)

        OutlinedButton(onClick = { retryTick++ }, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
    }
}

/** Not thread-safe by design — every call is expected from the same analyzer thread. */
private class GuardTracker {
    private val xs = ArrayDeque<Int>()
    private val ys = ArrayDeque<Int>()
    private var lastSeenAtMs = 0L

    fun reset() {
        xs.clear(); ys.clear(); lastSeenAtMs = 0L
    }

    fun observe(centerX: Int, centerY: Int, nowMs: Long): CalibrationState {
        lastSeenAtMs = nowMs
        xs.addLast(centerX); ys.addLast(centerY)
        if (xs.size > WINDOW_SIZE) { xs.removeFirst(); ys.removeFirst() }

        if (xs.size < WINDOW_SIZE) {
            return CalibrationState(
                statusText = "Marker found — stabilizing (${xs.size}/$WINDOW_SIZE)…",
                locked = false,
                sampleCount = xs.size,
            )
        }

        val spreadX = xs.max() - xs.min()
        val spreadY = ys.max() - ys.min()
        val jitter = sqrt((spreadX * spreadX + spreadY * spreadY).toFloat())
        val locked = jitter <= LOCK_JITTER_THRESHOLD_PX

        return CalibrationState(
            statusText = if (locked) "Marker locked." else "Marker visible, still settling…",
            locked = locked,
            jitterPx = jitter,
            sampleCount = xs.size,
        )
    }

    fun staleCheck(nowMs: Long): CalibrationState? {
        if (lastSeenAtMs != 0L && nowMs - lastSeenAtMs > STALE_AFTER_MS) {
            reset()
            return CalibrationState(statusText = "Marker lost — re-aim at the rig screen.", locked = false)
        }
        return null
    }
}

@Composable
private fun LabeledStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Spacer(dp: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.height(dp))
}

@Composable
private fun CalibrationCameraFeed(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onState: (CalibrationState) -> Unit,
) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val mainExecutor = ContextCompat.getMainExecutor(ctx)
            val tracker = GuardTracker()
            val decoder = MarkerDecoder()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { image ->
                            val result = decoder.decode(ImageProxyFrameSampler(image))
                            val now = System.currentTimeMillis()
                            val next = if (result != null) {
                                val cx = (result.guardBox.left + result.guardBox.right) / 2
                                val cy = (result.guardBox.top + result.guardBox.bottom) / 2
                                tracker.observe(cx, cy, now)
                            } else {
                                tracker.staleCheck(now)
                            }
                            if (next != null) mainExecutor.execute { onState(next) }
                            image.close()
                        }
                    }

                cameraProvider.unbindAll()
                // Back camera: this is the pairing the deck corrected — only the
                // back camera hits 120fps on this hardware, which also happens
                // to point the screen at the clinician rather than the patient.
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            }, mainExecutor)

            previewView
        }
    )
}
