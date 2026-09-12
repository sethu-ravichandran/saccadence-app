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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arra.saccadence.ui.components.LabeledTextField
import com.arra.saccadence.ui.components.MonoEyebrow
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.ScreenScaffold
import com.arra.saccadence.ui.components.ScreenTitle
import com.arra.saccadence.ui.components.SegmentedSelector
import com.arra.saccadence.ui.components.StatusBlock
import com.arra.saccadence.ui.components.StatusTone
import com.arra.saccadence.ui.components.TextAffordance
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Spacing
import com.arra.saccadence.voice.SpokenLanguage
import kotlinx.coroutines.launch

data class PairingSettings(val host: String, val port: Int, val sessionCode: String, val language: SpokenLanguage)

/**
 * The rig shows a pairing QR (encodes `ws://host:port/?join=CODE`, see
 * saccadence-rig/public/js/startScreen.js) plus the same code as text.
 * Scanning is the primary path; manual entry stays one tap away as a
 * fallback for a QR that won't scan (glare, cracked screen, etc).
 *
 * The handoff's pairing screen is manual-entry-only, so its field treatment,
 * language selector and status line are applied here while the scan-first
 * structure — which is a feature, not a style — is left alone. The scanner's
 * viewport borrows the handoff's media radius and its chips, so the live feed
 * reads as part of the same product as the forms around it.
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

    val canConnect = onWifi && if (manualEntry) {
        host.isNotBlank() && sessionCode.isNotBlank()
    } else {
        scanned != null
    }

    ScreenScaffold(
        bodyArrangement = Arrangement.spacedBy(Spacing.xl),
        footer = {
            if (statusText.isNotBlank()) {
                // Pairing progress is reported, never asserted — a pulsing
                // accent dot while it is still working, a settled green one
                // once the rig has joined.
                StatusBlock(
                    tone = when {
                        statusText.contains("✓") -> StatusTone.Good
                        statusText.contains("Disconnected") -> StatusTone.Warning
                        else -> StatusTone.Progress
                    },
                    title = statusText,
                )
            }
            PrimaryButton(
                label = "Connect",
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
            )
        },
    ) {
        ScreenTitle(
            title = "Pair with rig",
            subtitle = if (manualEntry) {
                "Enter the address and session code shown on the clinic laptop's start screen."
            } else {
                "Point the camera at the QR code on the clinic laptop's start screen."
            },
        )
        Text(
            text = "This device and the clinic laptop are expected to be on the same network.",
            style = SaccadenceType.BodySecondary,
            color = SaccadenceColors.InkFaintStrong,
        )

        if (!onWifi) {
            StatusBlock(
                tone = StatusTone.Warning,
                title = "No Wi-Fi connection detected.",
                detail = if (manualEntry) {
                    "Connect this device to the same Wi-Fi network as the clinic laptop before connecting."
                } else {
                    "Connect this device to the same Wi-Fi network as the clinic laptop before scanning."
                },
            )
        }

        if (!manualEntry) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(Radius.media)
                    .background(SaccadenceColors.DarkCardInner),
            ) {
                val address = scanned
                if (!onWifi) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Camera paused",
                            style = SaccadenceType.InstructionTitle,
                            color = SaccadenceColors.DarkText,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = "Waiting for Wi-Fi…",
                            style = SaccadenceType.Body,
                            color = SaccadenceColors.DarkTextSecondary,
                        )
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

            TextAffordance(
                label = "Enter address manually instead",
                onClick = { manualEntry = true },
            )
        } else {
            LabeledTextField(
                label = "Rig IP address",
                value = host,
                onValueChange = { host = it },
                placeholder = "192.168.1.42",
                textStyle = SaccadenceType.FieldValueMono,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledTextField(
                label = "Port",
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = SaccadenceType.FieldValueMono,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledTextField(
                label = "Session code",
                value = sessionCode,
                onValueChange = { sessionCode = it.uppercase() },
                placeholder = "ABC123",
                // Tracked wide, per the handoff — a code is read character by
                // character, not as a word.
                textStyle = SaccadenceType.FieldValueCode,
                modifier = Modifier.fillMaxWidth(),
            )
            TextAffordance(
                label = "Scan QR instead",
                onClick = { manualEntry = false; scanned = null; scannedBitmap = null },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
            MonoEyebrow("SPOKEN INSTRUCTIONS")
            SegmentedSelector(
                options = SpokenLanguage.entries,
                selected = language,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelect = { language = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Plays once a QR hit lands: a camera-shutter flash, a green line reading down the
 * captured frame, then the frame zooms in and dims as the confirmation text opens
 * on top of it — sells "we just read that code" instead of a flat freeze-frame.
 *
 * Timings are untouched by the re-skin; only the colours moved onto the
 * palette, so the laser now reads in the design's dark-surface green rather
 * than an off-palette iOS system green.
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
        // Pure black and, below, pure white: the dim and the shutter flash are
        // photographic effects on a camera frame, not palette surfaces, so they
        // are the two places in the UI that legitimately sit off-token.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.value)))

        if (readProgress.value < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * readProgress.value
                val laserColor = SaccadenceColors.DarkSuccessDot
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(laserColor.copy(alpha = 0f), laserColor, laserColor.copy(alpha = 0f)),
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 4.dp.toPx(),
                )
                drawRect(
                    color = laserColor.copy(alpha = 0.08f),
                    size = Size(size.width, y),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().alpha(textAlpha.value),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Scanned ✓",
                style = SaccadenceType.InstructionTitle,
                color = SaccadenceColors.DarkText,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                // Mono: an address and a code are read character by character.
                text = "${address.host}:${address.port} · ${address.sessionCode}",
                style = SaccadenceType.MonoVideoCaption,
                color = SaccadenceColors.DarkTextSecondary,
            )
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

    Canvas(modifier = modifier) {
        val boxSize = size.width * 0.6f
        val left = (size.width - boxSize) / 2
        val top = (size.height - boxSize) / 2
        val bracket = boxSize * 0.18f
        val stroke = Stroke(width = 4.dp.toPx())
        // The brackets take the dark-surface guide colour, the same one the
        // trial's alignment oval uses — both mean "line this up here".
        val color = SaccadenceColors.DarkAccentGuide.copy(alpha = pulse)

        // Four independent L-shaped brackets rather than one full rounded rect outline —
        // reads as "align the code here" without implying the corners must touch a border.
        listOf(
            Triple(left, top, Pair(1, 1)),
            Triple(left + boxSize, top, Pair(-1, 1)),
            Triple(left, top + boxSize, Pair(1, -1)),
            Triple(left + boxSize, top + boxSize, Pair(-1, -1)),
        ).forEach { (x, y, dir) ->
            val (dx, dy) = dir
            drawLine(color, Offset(x, y), Offset(x + bracket * dx, y), stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + bracket * dy), stroke.width, cap = StrokeCap.Round)
        }

        // Laser sweep, inset slightly so it never overlaps the corner brackets.
        val laserY = top + bracket / 2 + sweep * (boxSize - bracket)
        val laserColor = SaccadenceColors.DarkSuccessDot
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(laserColor.copy(alpha = 0f), laserColor, laserColor.copy(alpha = 0f)),
                startX = left,
                endX = left + boxSize,
            ),
            start = Offset(left, laserY),
            end = Offset(left + boxSize, laserY),
            strokeWidth = 3.dp.toPx(),
        )
    }
}
