package com.arra.saccadence.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.arra.saccadence.ui.theme.ChipMetrics
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The dark half of the design system: the rig stimulus mirror and the live eye
 * capture.
 *
 * These are **not** a dark mode. The handoff is explicit that the dark surfaces
 * share the light screens' accent and neutral ramps "so it reads as one product
 * in a different mode" — they are dark because a live stimulus and a camera
 * feed have to be, not because the user chose a theme. So they stay dark
 * regardless of the system setting, and they draw from the `Dark*` tokens
 * rather than from a second `ColorScheme`.
 */

/** A dark card — the stimulus mirror or capture pane. Radius 18, 1 px border. */
@Composable
fun DarkPane(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = Spacing.xl,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(Radius.darkCard)
            .background(SaccadenceColors.DarkCard)
            .border(Sizes.borderWidth, SaccadenceColors.DarkBorder, Radius.darkCard)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A chip floating over live video: a 72 %-opacity scrim behind mono 10 px text.
 * The scrim matters — a flat colour would fight whatever the camera is seeing.
 */
@Composable
fun VideoChip(
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = SaccadenceColors.DarkChipText,
    style: TextStyle = SaccadenceType.MonoVideoChip,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(Radius.chip)
            .background(SaccadenceColors.DarkChipScrim.copy(alpha = 0.72f))
            .padding(horizontal = ChipMetrics.videoPaddingH, vertical = ChipMetrics.videoPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text = label, style = style, color = textColor)
    }
}

/**
 * The recording chip: a pulsing red dot plus a mono label. The handoff runs
 * this dot at 1.1 s rather than the status dot's 1.2 s — close enough to read
 * as the same language, different enough that the two never lock into sync.
 */
@Composable
fun RecordingChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    VideoChip(
        label = label,
        modifier = modifier,
        leading = {
            StatusDot(
                color = SaccadenceColors.RecordingDot,
                pulsing = true,
                size = Sizes.recordingDot,
                periodMillis = 1100,
            )
        },
    )
}

/**
 * A chip over the calibration preview. Same idea as [VideoChip] but on the
 * neutral 265 hue and at 60 % — the handoff keeps the calibration preview's
 * chrome a shade cooler than the trial's.
 */
@Composable
fun PreviewChip(
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = SaccadenceColors.CalibChipText,
    style: TextStyle = SaccadenceType.MonoVideoChip,
) {
    Box(
        modifier = modifier
            .clip(Radius.tag)
            .background(SaccadenceColors.CalibChipScrim.copy(alpha = 0.6f))
            .padding(horizontal = ChipMetrics.previewPaddingH, vertical = ChipMetrics.previewPaddingV),
    ) {
        Text(text = label, style = style, color = textColor)
    }
}

/** The three states the dark quality line reports. */
enum class CaptureQuality {
    /** Tracking cleanly. Green dot, bright label. */
    Good,

    /** Degraded — a blink or a lost frame. Amber dot, amber label. */
    Degraded,

    /** Nothing useful being captured yet. Neutral. */
    Idle,
}

/**
 * The quality line beneath a live feed: a 1 px top border, an 8 px status dot,
 * a 600/14 label, and an optional right-aligned mono counter.
 *
 * This is the patient-facing reassurance channel during the trial — there are
 * no controls, so this line and the phase bar are the only things telling them
 * it is working.
 */
@Composable
fun CaptureQualityLine(
    quality: CaptureQuality,
    label: String,
    modifier: Modifier = Modifier,
    counter: String? = null,
) {
    val dotColor = when (quality) {
        CaptureQuality.Good -> SaccadenceColors.DarkSuccessDot
        CaptureQuality.Degraded -> SaccadenceColors.DarkWarnDot
        CaptureQuality.Idle -> SaccadenceColors.DarkMonoLabel
    }
    val labelColor = when (quality) {
        CaptureQuality.Good -> SaccadenceColors.DarkGoodLabel
        CaptureQuality.Degraded -> SaccadenceColors.DarkWarnLabel
        CaptureQuality.Idle -> SaccadenceColors.DarkTextSecondary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.borderWidth)
                .background(SaccadenceColors.DarkBorder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = dotColor, size = Sizes.qualityDot)
            Spacer(Modifier.width(Spacing.smPlus))
            Text(
                text = label,
                style = SaccadenceType.StatusLine,
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            if (counter != null) {
                Text(
                    text = counter,
                    style = SaccadenceType.MonoCounter,
                    color = SaccadenceColors.DarkMonoLabel,
                )
            }
        }
    }
}

/**
 * The bottom-centre prompt the handoff shows when capture degrades — "Open your
 * eyes wide". Bold, on a scrim, and only ever shown when there is something the
 * patient can actually do about it.
 */
@Composable
fun CapturePrompt(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(Radius.chip)
            .background(SaccadenceColors.DarkChipScrim.copy(alpha = 0.78f))
            .padding(horizontal = Spacing.mdPlus, vertical = Spacing.sm),
    ) {
        Text(
            text = text,
            style = SaccadenceType.BodySmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
            ),
            color = SaccadenceColors.DarkWarnPrompt,
        )
    }
}

/**
 * The dark pane's eyebrow + instruction block: a widely-tracked mono label over
 * an 800/25 title and a 15 px sub-line.
 */
@Composable
fun StimulusInstruction(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.mdPlus)) {
        Text(
            text = eyebrow,
            style = SaccadenceType.MonoPaneEyebrow,
            color = SaccadenceColors.DarkMonoLabel,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = title,
                style = SaccadenceType.InstructionTitle,
                color = SaccadenceColors.DarkText,
            )
            Text(
                text = subtitle,
                style = SaccadenceType.Body,
                color = SaccadenceColors.DarkTextSecondary,
            )
        }
    }
}

/** The mono caption under a mirror naming the rig parameter in play. */
@Composable
fun MirrorCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SaccadenceType.MonoVideoChip,
        color = SaccadenceColors.DarkMonoCaption,
        modifier = modifier,
    )
}

/** The dark phase header: a mono label on each side, above the segmented bar. */
@Composable
fun PhaseHeader(
    left: String,
    right: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = left, style = SaccadenceType.MonoStatLabel, color = SaccadenceColors.DarkMonoChipText)
        Text(text = right, style = SaccadenceType.MonoStatLabel, color = SaccadenceColors.DarkMonoChipText)
    }
}

/** A warning strip on a dark surface — e.g. a dropped rig connection mid-trial. */
@Composable
fun DarkWarningStrip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SaccadenceColors.DarkCard)
            .padding(horizontal = Spacing.screen, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = SaccadenceColors.DarkWarnDot, pulsing = true, size = Sizes.qualityDot)
        Spacer(Modifier.width(Spacing.smPlus))
        Text(
            text = text,
            style = SaccadenceType.BodySmall,
            color = SaccadenceColors.DarkWarnLabel,
        )
    }
}

/**
 * A rounded inner surface inside a dark pane — the mirror area itself.
 */
@Composable
fun DarkInnerSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Radius.innerSurface,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(SaccadenceColors.DarkCardInner),
        content = content,
    )
}
