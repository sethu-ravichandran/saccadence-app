package com.arra.saccadence.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Wraps the app in the Saccadence design system.
 *
 * Screen code should read tokens directly — [SaccadenceColors], [SaccadenceType],
 * [Spacing], [Radius], [Sizes] — and build UI from `ui.components`. So why set up
 * a `MaterialTheme` at all? Because a handful of Material components are still
 * the right tool (`AlertDialog`, `Checkbox`, the press ripple) and they read
 * their colours from `MaterialTheme` whether we like it or not. Mapping the
 * scheme onto our tokens means those components land on-palette instead of on
 * Material's default purple, and it means any spot that still says
 * `MaterialTheme.typography.bodyMedium` degrades to correct Manrope rather
 * than to Roboto.
 *
 * Treat the Material mapping as a safety net, not an interface. New UI should
 * not be written against `MaterialTheme.*`.
 */
@Composable
fun SaccadenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaccadenceColorScheme,
        typography = SaccadenceTypography,
        content = content,
    )
}

/**
 * The design is a single light theme. The dark surfaces (rig mirror, live
 * capture) are *specific components* that are always dark — they are not a
 * dark mode, so there is deliberately no dark `ColorScheme` here. A clinician
 * with the system in dark mode still gets the white intake forms the handoff
 * specifies, because a screening reading has to look the same in every room.
 */
private val SaccadenceColorScheme = lightColorScheme(
    primary = SaccadenceColors.Accent,
    onPrimary = SaccadenceColors.Surface,
    primaryContainer = SaccadenceColors.AccentTint,
    onPrimaryContainer = SaccadenceColors.Accent,
    secondary = SaccadenceColors.InkSecondary,
    onSecondary = SaccadenceColors.Surface,
    background = SaccadenceColors.Surface,
    onBackground = SaccadenceColors.Ink,
    surface = SaccadenceColors.Surface,
    onSurface = SaccadenceColors.Ink,
    surfaceVariant = SaccadenceColors.SurfaceSunken,
    onSurfaceVariant = SaccadenceColors.InkMuted,
    outline = SaccadenceColors.Border,
    outlineVariant = SaccadenceColors.Hairline,
    error = SaccadenceColors.Danger,
    onError = SaccadenceColors.Surface,
    errorContainer = SaccadenceColors.WarningSurface,
    onErrorContainer = SaccadenceColors.WarningInk,
)

/** Material slots mapped onto the handoff's scale, closest role wins. */
private val SaccadenceTypography = Typography(
    displayLarge = SaccadenceType.Metric,
    displayMedium = SaccadenceType.Metric,
    displaySmall = SaccadenceType.Metric,
    headlineLarge = SaccadenceType.H1,
    headlineMedium = SaccadenceType.H1,
    headlineSmall = SaccadenceType.H2,
    titleLarge = SaccadenceType.SectionTitle,
    titleMedium = SaccadenceType.RowTitle,
    titleSmall = SaccadenceType.FieldLabel,
    bodyLarge = SaccadenceType.FieldValue,
    bodyMedium = SaccadenceType.Body,
    bodySmall = SaccadenceType.BodySmall,
    labelLarge = SaccadenceType.ButtonSecondary,
    labelMedium = SaccadenceType.MonoEyebrow,
    labelSmall = SaccadenceType.MonoTag,
)
