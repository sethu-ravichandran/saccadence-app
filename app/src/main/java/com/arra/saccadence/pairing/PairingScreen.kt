package com.arra.saccadence.pairing

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arra.saccadence.voice.SpokenLanguage
import kotlinx.coroutines.launch

data class PairingSettings(val host: String, val port: Int, val sessionCode: String, val language: SpokenLanguage)

/**
 * The rig shows a pairing QR (encodes `ws://host:port/?join=CODE`, see
 * saccadence-rig/public/js/startScreen.js) plus the same code as text.
 * Scanning is the primary path; manual entry stays one tap away as a
 * fallback for a QR that won't scan (glare, cracked screen, etc).
 */
@Composable
fun PairingScreen(
    statusText: String,
    onConnect: (PairingSettings) -> Unit,
) {
    var manualEntry by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<ScannedRigAddress?>(null) }
    var scannedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var sessionCode by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(SpokenLanguage.ENGLISH) }
    val onWifi = rememberIsOnWifi()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Pair with rig", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))

        if (!manualEntry) {
            Text(
                "Point the camera at the QR code on the clinic laptop's start screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "This device and the clinic laptop are expected to be on the same network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            if (!onWifi) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                ) {
                    Text(
                        "No Wi-Fi connection detected. Connect this device to the same Wi-Fi network as the clinic laptop before scanning.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
            ) {
                val address = scanned
                if (!onWifi) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Camera paused", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Waiting for Wi-Fi…", color = Color(0xFFB5C0D0))
                    }
                } else if (address == null) {
                    QrScannerFeed(
                        onScanned = { addr, bitmap -> scanned = addr; scannedBitmap = bitmap },
                        modifier = Modifier.fillMaxSize(),
                    )
                    ScanReticle(modifier = Modifier.fillMaxSize())
                } else {
                    // Freeze on the captured frame rather than restarting the scanner —
                    // nothing to gain from re-scanning once we already have it.
                    ScanCaptureAnimation(
                        bitmap = scannedBitmap,
                        address = address,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { manualEntry = true }) { Text("Enter address manually instead") }
        } else {
            Text(
                "Enter the address and session code shown on the clinic laptop's start screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "This device and the clinic laptop are expected to be on the same network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            if (!onWifi) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                ) {
                    Text(
                        "No Wi-Fi connection detected. Connect this device to the same Wi-Fi network as the clinic laptop before connecting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = host, onValueChange = { host = it },
                label = { Text("Rig IP address") },
                placeholder = { Text("192.168.1.42") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = sessionCode, onValueChange = { sessionCode = it.uppercase() },
                label = { Text("Session code") },
                placeholder = { Text("ABC123") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { manualEntry = false; scanned = null; scannedBitmap = null }) { Text("Scan QR instead") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Spoken instructions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SpokenLanguage.entries.forEachIndexed { index, option ->
                if (index > 0) Spacer(Modifier.width(8.dp))
                val label = option.name.lowercase().replaceFirstChar { it.uppercase() }
                if (option == language) {
                    Button(onClick = { language = option }, modifier = Modifier.weight(1f)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { language = option }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(statusText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))

        val canConnect = onWifi && if (manualEntry) host.isNotBlank() && sessionCode.isNotBlank() else scanned != null
        Button(
            onClick = {
                val settings = if (manualEntry) {
                    PairingSettings(host.trim(), port.toIntOrNull() ?: 8765, sessionCode.trim(), language)
                } else {
                    val address = scanned!!
                    PairingSettings(address.host, address.port, address.sessionCode, language)
                }
                onConnect(settings)
            },
            enabled = canConnect,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect") }
    }
}

/**
 * Plays once a QR hit lands: a camera-shutter flash, a green line reading down the
 * captured frame, then the frame zooms in and dims as the confirmation text opens
 * on top of it — sells "we just read that code" instead of a flat freeze-frame.
 */
@Composable
private fun ScanCaptureAnimation(bitmap: Bitmap?, address: ScannedRigAddress, modifier: Modifier = Modifier) {
    val flash = remember { Animatable(1f) }
    val readProgress = remember { Animatable(0f) }
    val zoom = remember { Animatable(1f) }
    val dim = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(address) {
        flash.animateTo(0f, tween(durationMillis = 220))
        readProgress.animateTo(1f, tween(durationMillis = 500, easing = LinearEasing))
        launch { zoom.animateTo(1.12f, tween(durationMillis = 450, easing = EaseOutBack)) }
        launch { dim.animateTo(0.6f, tween(durationMillis = 350)) }
        textAlpha.animateTo(1f, tween(durationMillis = 300))
    }

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scale(zoom.value),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.value)))

        if (readProgress.value < 1f) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * readProgress.value
                val laserColor = Color(0xFF4CD964)
                drawLine(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(laserColor.copy(alpha = 0f), laserColor, laserColor.copy(alpha = 0f)),
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 4.dp.toPx(),
                )
                drawRect(
                    color = laserColor.copy(alpha = 0.08f),
                    size = androidx.compose.ui.geometry.Size(size.width, y),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().alpha(textAlpha.value),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Scanned ✓", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("${address.host}:${address.port} · ${address.sessionCode}", color = Color(0xFFB5C0D0))
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
    }
}

/**
 * Four corner brackets over the live feed plus a laser line sweeping up and down
 * between them — the standard "actively scanning" affordance for a QR scan.
 */
@Composable
private fun ScanReticle(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "qr-scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val boxSize = size.width * 0.6f
        val left = (size.width - boxSize) / 2
        val top = (size.height - boxSize) / 2
        val bracket = boxSize * 0.18f
        val stroke = Stroke(width = 4.dp.toPx())
        val color = Color.White.copy(alpha = pulse)

        // Four independent L-shaped brackets rather than one full rounded rect outline —
        // reads as "align the code here" without implying the corners must touch a border.
        listOf(
            Triple(left, top, Pair(1, 1)),
            Triple(left + boxSize, top, Pair(-1, 1)),
            Triple(left, top + boxSize, Pair(1, -1)),
            Triple(left + boxSize, top + boxSize, Pair(-1, -1)),
        ).forEach { (x, y, dir) ->
            val (dx, dy) = dir
            drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + bracket * dx, y), stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + bracket * dy), stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }

        // Laser sweep, inset slightly so it never overlaps the corner brackets.
        val laserY = top + bracket / 2 + sweep * (boxSize - bracket)
        val laserColor = Color(0xFF4CD964)
        drawLine(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(laserColor.copy(alpha = 0f), laserColor, laserColor.copy(alpha = 0f)),
                startX = left,
                endX = left + boxSize,
            ),
            start = androidx.compose.ui.geometry.Offset(left, laserY),
            end = androidx.compose.ui.geometry.Offset(left + boxSize, laserY),
            strokeWidth = 3.dp.toPx(),
        )
    }
}
