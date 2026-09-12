package com.arra.saccadence.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The handoff's spacing scale: 4 · 6 · 8 · 10 · 12 · 14 · 16 · 20 · 22 · 24.
 * Nothing outside this set — if a gap feels like it wants 18, it wants 16 or 20.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val smPlus = 10.dp
    val md = 12.dp
    val mdPlus = 14.dp
    val lg = 16.dp
    val xl = 20.dp
    val xlPlus = 22.dp
    val xxl = 24.dp

    /** Screen padding, on every screen's body. */
    val screen = 24.dp
}

/**
 * The handoff's radius bands: 5–6 chips/tags · 10–12 fields and status blocks ·
 * 14 buttons · 16–18 media and dark cards · 999 pills.
 */
object Radius {
    val tag = RoundedCornerShape(5.dp)
    val chip = RoundedCornerShape(6.dp)
    val statusBlock = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(12.dp)
    val innerSurface = RoundedCornerShape(12.dp)
    val statCard = RoundedCornerShape(12.dp)
    val infoBlock = RoundedCornerShape(10.dp)
    val button = RoundedCornerShape(14.dp)
    val media = RoundedCornerShape(16.dp)
    val darkCard = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(999.dp)
}

/**
 * Fixed component metrics the handoff specifies outright, kept here so a
 * button's height can't quietly drift between screens.
 */
object Sizes {
    /** Primary filled button. */
    val buttonHeight = 54.dp

    /** Outlined button — the handoff's 46–50 band; 48 is its midpoint. */
    val buttonHeightSecondary = 48.dp

    /** The compact outlined button used for "View / erase saved patients". */
    val buttonHeightCompact = 46.dp

    /** Language selector segment. */
    val selectorHeight = 46.dp

    /** Text field. */
    val fieldHeight = 52.dp

    /** Multiline notes field. */
    val fieldHeightMultiline = 62.dp

    /** The status / recording dot. */
    val statusDot = 9.dp

    /** The smaller dot on a result quality badge. */
    val badgeDot = 8.dp

    /** The dot on the dark quality line. */
    val qualityDot = 8.dp

    /** The recording chip's red dot. */
    val recordingDot = 7.dp

    /** Checkbox in the symptom checklist. */
    val checkbox = 22.dp

    /** Track height of the intake step bar, and of a phase-bar segment. */
    val progressTrack = 4.dp

    /** The camera preview on the calibration screen. */
    val cameraPreviewHeight = 250.dp

    /** Width of the fixed-width Back button in an intake footer. */
    val backButtonWidth = 110.dp

    /** The narrower Back button on the symptoms footer. */
    val backButtonWidthNarrow = 92.dp

    /** Gap between stacked actions inside a pinned footer. */
    val footerGap = 9.dp

    /** Border width of a resting outline. */
    val borderWidth = 1.dp

    /** Border width of a focused field or an emphasized outline button. */
    val borderWidthEmphasis = 1.5.dp
}

/**
 * Chip and tag padding. The handoff gives these as 4/5/7/8/9/13 px pairs,
 * which sit off the layout spacing scale on purpose: they are intrinsic
 * component metrics, not gaps between things, so they live here under their
 * own names instead of being bent onto [Spacing].
 */
object ChipMetrics {
    /** A tappable pill (RECENT doctor chips): 9 px / 13 px. */
    val pillPaddingH = 13.dp
    val pillPaddingV = 9.dp

    /** A DEMO-style tag: 4 px / 7 px. */
    val tagPaddingH = 7.dp
    val tagPaddingV = 4.dp

    /** A stage-ribbon chip: 5 px / 9 px. */
    val stagePaddingH = 9.dp
    val stagePaddingV = 5.dp

    /** A scrim chip over live video: 5 px / 9 px. */
    val videoPaddingH = 9.dp
    val videoPaddingV = 5.dp

    /** A chip over the calibration preview: 4 px / 8 px. */
    val previewPaddingH = 8.dp
    val previewPaddingV = 4.dp
}
