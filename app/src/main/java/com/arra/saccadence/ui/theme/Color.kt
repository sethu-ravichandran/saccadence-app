package com.arra.saccadence.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The design system's colour tokens, transcribed from `design-handoff/README.md`.
 *
 * The handoff authors every colour in `oklch(...)` and supplies hex only as a
 * convenience "for tools that need them". These constants are the **exact**
 * oklch → sRGB conversions (standard OKLab matrices, then sRGB transfer
 * function), not the handoff's stated approximations: the design board is an
 * HTML file whose CSS carries the raw `oklch()` values, so the browser's own
 * conversion — which is what this reproduces — is what the designer is
 * actually looking at. Several of the handoff's approximations drift a
 * noticeable amount from that (its accent `#6C5BC4` vs. the true `#6B66B4`),
 * so matching the board means converting rather than copying.
 *
 * Token names are roles, not appearances. Never reach for a raw
 * `Color(0xFF...)` in screen code — if a role is missing, add it here so the
 * next screen can share it.
 */
object SaccadenceColors {

    // ---- Accent -------------------------------------------------------------
    /** Primary action, selected state, focused field border, locked-focus. */
    val Accent = Color(0xFF6B66B4)

    /** Accent under press. Material's ripple rides on top of the filled colour. */
    val AccentPressed = Color(0xFF504799)

    /** Tint behind informational surfaces (returning-patient lookup message). */
    val AccentTint = Color(0xFFF0F0FF)

    /** Slightly lighter tint, used behind the pairing status line. */
    val AccentTint2 = Color(0xFFF3F4FF)

    // ---- Neutral ink --------------------------------------------------------
    /** Headings and primary body text. */
    val Ink = Color(0xFF161B24)

    /** Field labels, secondary-button labels, sub-headings. */
    val InkSecondary = Color(0xFF484D57)

    /** Explanatory body text under a title. */
    val InkMuted = Color(0xFF5E636E)

    /** The 0.52-lightness step of the muted ramp — checklist explainers. */
    val InkMutedStrong = Color(0xFF646974)

    /** Mono metadata lines and step-bar labels. */
    val InkMutedMono = Color(0xFF656972)

    /** Captions and the faintest supporting text. */
    val InkFaint = Color(0xFF767A84)

    /** The 0.55-lightness step — status-bar text, footer captions, demo labels. */
    val InkFaintStrong = Color(0xFF6D727B)

    /** Text-field placeholders. */
    val Placeholder = Color(0xFF8B8F99)

    // ---- Lines and surfaces -------------------------------------------------
    /** Hairline separators between list and consent rows; footer top border. */
    val Hairline = Color(0xFFDFE1E7)

    /** The 0.93-lightness hairline used between patient-list rows. */
    val HairlineLight = Color(0xFFE5E8ED)

    /** Resting border on outlined buttons and the language selector. */
    val Border = Color(0xFFCED1D8)

    /** Resting border on text fields (a half-step lighter than [Border]). */
    val BorderField = Color(0xFFD1D4DB)

    /** The dashed rule above the low-emphasis demo entry point. */
    val BorderDashed = Color(0xFFD5D7DD)

    /** The app's base surface. */
    val Surface = Color(0xFFFFFFFF)

    /** Sunken surface for stat cards and inner panels. */
    val SurfaceSunken = Color(0xFFF4F5F8)

    /** Background for tags, recent-doctor chips, and completed stage chips. */
    val ChipSurface = Color(0xFFE8EBF1)

    /** Background of a disabled primary button. */
    val DisabledSurface = Color(0xFFE6E8EC)

    /** Label on a disabled primary button, and future stage-chip text. */
    val DisabledLabel = Color(0xFF9598A0)

    /** Background of the synthetic demo row in the patient list. */
    val DemoRowSurface = Color(0xFFF7F8FB)

    /** Step-bar track behind the accent fill. */
    val StepTrack = Color(0xFFE2E5EA)

    // ---- Success / locked ---------------------------------------------------
    val SuccessDot = Color(0xFF187C49)
    val SuccessInk = Color(0xFF154F2F)
    val SuccessInkStrong = Color(0xFF0E492A)
    val SuccessReason = Color(0xFF31573F)

    /** An in-range or improved metric value. */
    val SuccessValue = Color(0xFF095C34)
    val SuccessSurface = Color(0xFFD5F9E0)

    // ---- Warning / stale / flagged -----------------------------------------
    val WarningDot = Color(0xFFC8800D)
    val WarningInk = Color(0xFF713F00)
    val WarningInkStrong = Color(0xFF6B3A00)

    /** The second, indented line inside a warning status block. */
    val WarningInkAlt = Color(0xFF704D29)

    /** An out-of-range metric value or short validity count. */
    val WarningValue = Color(0xFF915200)
    val WarningSurface = Color(0xFFFFEECD)

    // ---- Danger -------------------------------------------------------------
    /** Label of the destructive outlined button. */
    val Danger = Color(0xFFA43B38)

    /** Border of the destructive outlined button. */
    val DangerBorder = Color(0xFFECA19A)

    /** The pulsing dot on the recording chip. */
    val RecordingDot = Color(0xFFDE4E4B)

    // ---- Dark surfaces (rig mirror + live capture) --------------------------
    /** The dark shell background. */
    val DarkScreen = Color(0xFF111219)

    /** A card sitting on the dark shell (stimulus mirror, eye-capture pane). */
    val DarkCard = Color(0xFF1B1E27)

    /** The inner mirror area inside a dark card. */
    val DarkCardInner = Color(0xFF14161D)

    val DarkBorder = Color(0xFF2B2D38)
    val DarkText = Color(0xFFF4F5F8)
    val DarkTextSecondary = Color(0xFFAEB1BB)

    /** Mono eyebrow labels on dark ("RIG STIMULUS"). */
    val DarkMonoLabel = Color(0xFF9498A5)

    /** The mono caption naming the rig parameter, under the mirror. */
    val DarkMonoCaption = Color(0xFF7C808D)

    /** Mono text in the dark phase header. */
    val DarkMonoChipText = Color(0xFF9B9EA8)

    /** Text inside a scrim chip over live video. */
    val DarkChipText = Color(0xFFE6E7EF)

    /** The stimulus target dot and other dark-mode accents. */
    val DarkAccent = Color(0xFFAFABFF)

    /** Canvas-drawn alignment guides (ring, oval) on dark. */
    val DarkAccentGuide = Color(0xFF9E9CE1)

    /** The crosshair at the centre of the eye-alignment guide. */
    val DarkCrosshair = Color(0xFFB7B5FC)

    /** A not-yet-reached segment of the phase bar. */
    val DarkSegmentPending = Color(0xFF30323D)

    /** The dimmed previous-target dot during the saccade phase. */
    val DarkPrevTarget = Color(0xFF434758)

    /** The rail the target slides along during the pursuit phase. */
    val DarkRail = Color(0xFF363647)

    /** Base colour of a chip scrim over video; the handoff applies it at 72 %. */
    val DarkChipScrim = Color(0xFF101116)

    /** Quality-line dot and label when tracking is good. */
    val DarkSuccessDot = Color(0xFF4EBE7D)
    val DarkGoodLabel = Color(0xFFDCDEE5)

    /** Quality-line dot, label, and prompt when a blink degrades the sweep. */
    val DarkWarnDot = Color(0xFFE49E22)
    val DarkWarnLabel = Color(0xFFF3E6D2)
    val DarkWarnPrompt = Color(0xFFFDF0DC)

    /** The alignment oval when it has gone dashed/degraded. */
    val DarkGuideStale = Color(0xFFE8A95C)

    /** Home indicator pill on a dark screen. */
    val DarkHomeIndicator = Color(0xFF444753)

    // ---- Calibration preview (dark, but on the neutral 265 hue) -------------
    /** Placeholder stripes standing in for the live camera surface. */
    val CalibStripeA = Color(0xFF282B31)
    val CalibStripeB = Color(0xFF1E2026)

    /** Base colour of a calibration chip scrim; the handoff applies it at 60 %. */
    val CalibChipScrim = Color(0xFF14161B)
    val CalibChipText = Color(0xFFC7CEDB)

    /** The centred alignment ring; the handoff applies it at 85 %. */
    val CalibRing = Color(0xFFA7A5EB)

    /** The ring when calibration has gone stale (drawn dashed). */
    val CalibRingStale = Color(0xFFDEA052)

    /** Bottom-chip text in the stale calibration state. */
    val StaleChipText = Color(0xFFF4D9BB)

    /** Stripes for the eye-capture placeholder on the 275 hue. */
    val DarkStripeA = Color(0xFF2C2D35)
    val DarkStripeB = Color(0xFF22242B)
}
