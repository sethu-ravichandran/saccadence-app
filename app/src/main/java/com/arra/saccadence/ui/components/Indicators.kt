package com.arra.saccadence.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The three status tones the handoff uses across pairing, calibration and
 * results. A tone carries the whole treatment — surface, dot, title colour —
 * so a screen picks a *meaning*, not a colour.
 */
enum class StatusTone {
    /** In progress, waiting, connecting. Accent family; dot pulses. */
    Progress,

    /** Locked, paired, good. Success family; dot is static. */
    Good,

    /** Stale, flagged, blocked. Warning family; dot is static. */
    Warning,
}

private val StatusTone.surface: Color
    get() = when (this) {
        StatusTone.Progress -> SaccadenceColors.AccentTint2
        StatusTone.Good -> SaccadenceColors.SuccessSurface
        StatusTone.Warning -> SaccadenceColors.WarningSurface
    }

private val StatusTone.dot: Color
    get() = when (this) {
        StatusTone.Progress -> SaccadenceColors.Accent
        StatusTone.Good -> SaccadenceColors.SuccessDot
        StatusTone.Warning -> SaccadenceColors.WarningDot
    }

private val StatusTone.title: Color
    get() = when (this) {
        StatusTone.Progress -> SaccadenceColors.Accent
        StatusTone.Good -> SaccadenceColors.SuccessInk
        StatusTone.Warning -> SaccadenceColors.WarningInk
    }

private val StatusTone.detail: Color
    get() = when (this) {
        StatusTone.Progress -> SaccadenceColors.Accent
        StatusTone.Good -> SaccadenceColors.SuccessReason
        StatusTone.Warning -> SaccadenceColors.WarningInkAlt
    }

/**
 * A status block: tinted surface, a status dot, a title, and an optional
 * second line indented to sit under the title rather than under the dot.
 *
 * The dot pulses for [StatusTone.Progress] and holds steady otherwise — the
 * handoff makes that distinction load-bearing, because a pulsing dot means
 * "still working" and a static one means "settled".
 */
@Composable
fun StatusBlock(
    tone: StatusTone,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.statusBlock)
            .background(tone.surface)
            .padding(horizontal = Spacing.mdPlus, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = tone.dot, pulsing = tone == StatusTone.Progress)
            Spacer(Modifier.width(Spacing.smPlus))
            Text(
                text = title,
                style = if (tone == StatusTone.Progress) SaccadenceType.StatusLine else SaccadenceType.StatusTitle,
                color = tone.title,
            )
        }
        if (detail != null) {
            // Indented past the dot + its gap so the two lines share a left edge.
            Text(
                text = detail,
                style = SaccadenceType.BodySmall,
                color = tone.detail,
                modifier = Modifier.padding(start = Sizes.statusDot + Spacing.smPlus),
            )
        }
    }
}

/**
 * A status dot. When [pulsing], its opacity runs 1 → 0.35 → 1 over 1.2 s
 * ease-in-out, infinitely — the handoff's exact spec, shared by the pairing
 * dot, the in-progress calibration dot, and (at 1.1 s) the recording dot.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    size: Dp = Sizes.statusDot,
    periodMillis: Int = 1200,
) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status-dot")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = periodMillis / 2, easing = androidx.compose.animation.core.EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status-dot-alpha",
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

/**
 * A stat readout card — the calibration screen's FPS / JITTER / DROPPED row.
 * Mono label above a mono 700/22 value, with the unit trailing at 12 px / 400
 * so the number stays the thing you read first.
 *
 * [valueColor] is the state channel: ink normally, warning when the value is
 * outside tolerance. The surface and label colours are parameters because this
 * card is also used over the live camera feed, where the light sunken surface
 * would punch a hole in the dark pane — see [darkStatCardColors].
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = SaccadenceColors.Ink,
    colors: StatCardColors = StatCardColors(),
) {
    Column(
        modifier = modifier
            .clip(Radius.statCard)
            .background(colors.surface)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = label, style = SaccadenceType.MonoStatLabel, color = colors.label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = SaccadenceType.Stat, color = valueColor)
            if (unit != null) {
                Spacer(Modifier.width(Spacing.xxs))
                Text(
                    text = unit,
                    style = SaccadenceType.StatUnit,
                    color = colors.label,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/** Surface and label colours for a [StatCard]; defaults are the light screens'. */
data class StatCardColors(
    val surface: Color = SaccadenceColors.SurfaceSunken,
    val label: Color = SaccadenceColors.InkFaintStrong,
)

/**
 * [StatCard] colours for a card floating over live video: a scrim rather than
 * a solid, so the feed stays readable behind it.
 */
fun darkStatCardColors() = StatCardColors(
    surface = SaccadenceColors.DarkChipScrim.copy(alpha = 0.72f),
    label = SaccadenceColors.DarkMonoLabel,
)

/**
 * The intake step bar: "STEP n OF total" on the left, the step's name on the
 * right, and a 4 px track filled to n/total in accent.
 *
 * The fill animates because the app slides between steps rather than cutting —
 * a jumping bar under a sliding screen reads as two separate transitions.
 */
@Composable
fun StepBar(
    step: Int,
    total: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val target = (step.toFloat() / total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 240),
        label = "step-bar",
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "STEP $step OF $total",
                style = SaccadenceType.MonoEyebrow,
                color = SaccadenceColors.InkMutedMono,
            )
            Text(
                text = label,
                style = SaccadenceType.MonoEyebrow,
                color = SaccadenceColors.InkMutedMono,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.progressTrack)
                .clip(Radius.tag)
                .background(SaccadenceColors.StepTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(Sizes.progressTrack)
                    .background(SaccadenceColors.Accent, Radius.tag),
            )
        }
    }
}

/**
 * The informational block the handoff puts under the name field for a
 * returning patient: accent tint, a mono glyph, and a 13 px explanation.
 * Tappable when [onClick] is given.
 */
@Composable
fun InfoNote(
    glyph: String,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier
        .fillMaxWidth()
        .clip(Radius.infoBlock)
        .background(SaccadenceColors.AccentTint)
    Row(
        modifier = modifier
            .then(base)
            .then(if (onClick != null) Modifier.clickableRow(onClick) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = glyph,
            style = SaccadenceType.MonoEyebrow,
            color = SaccadenceColors.Accent,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = SaccadenceType.BodySmall,
            color = SaccadenceColors.Accent,
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/** A centred caption under a footer's action stack. */
@Composable
fun FooterCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SaccadenceType.Caption,
        color = SaccadenceColors.InkFaintStrong,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The dark phase bar: equal 4 px segments with a 4 px gap, accent for done and
 * current, dim for pending. Used on the live rig panel.
 */
@Composable
fun SegmentedPhaseBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.progressTrack)
                    .background(
                        color = if (index <= current) {
                            SaccadenceColors.DarkAccentGuide
                        } else {
                            SaccadenceColors.DarkSegmentPending
                        },
                        shape = Radius.tag,
                    ),
            )
        }
    }
}
