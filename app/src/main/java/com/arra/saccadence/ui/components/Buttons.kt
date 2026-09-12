package com.arra.saccadence.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.arra.saccadence.ui.theme.ChipMetrics
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The four button treatments the handoff defines, and no others. Each one is
 * built from `clickable` rather than a Material `Button` so the height, radius
 * and label weight are exactly the handoff's — but `clickable`'s default
 * indication is the platform ripple, which is what the handoff asks for
 * ("use the platform's standard ripple/press state").
 *
 * The filled button additionally darkens its own background to
 * [SaccadenceColors.AccentPressed] while pressed, per the handoff, with the
 * ripple riding on top.
 */

/** Primary filled action. Height 54, radius 14, accent fill, white 700/16 label. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelStyle: TextStyle = SaccadenceType.ButtonPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val background = when {
        !enabled -> SaccadenceColors.DisabledSurface
        pressed -> SaccadenceColors.AccentPressed
        else -> SaccadenceColors.Accent
    }
    val labelColor = if (enabled) SaccadenceColors.Surface else SaccadenceColors.DisabledLabel

    Box(
        modifier = modifier
            .height(Sizes.buttonHeight)
            .clip(Radius.button)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = labelStyle, color = labelColor, textAlign = TextAlign.Center)
    }
}

/** Outlined secondary action. 1 px border, ink-secondary 600/15 label. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = Sizes.buttonHeightSecondary,
    labelStyle: TextStyle = SaccadenceType.ButtonSecondary,
) {
    OutlineButtonBase(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = height,
        borderWidth = Sizes.borderWidth,
        borderColor = SaccadenceColors.Border,
        labelColor = SaccadenceColors.InkSecondary,
        labelStyle = labelStyle,
    )
}

/**
 * Outlined, but in accent at 1.5 px and weight 700 — the handoff's treatment
 * for an outlined action that is nonetheless the *recommended* one (Retry when
 * the primary action is blocked). Reserve it for exactly that: if everything
 * is emphasised, the emphasis stops meaning anything.
 */
@Composable
fun EmphasizedOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = Sizes.buttonHeightSecondary,
) {
    OutlineButtonBase(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = height,
        borderWidth = Sizes.borderWidthEmphasis,
        borderColor = SaccadenceColors.Accent,
        labelColor = SaccadenceColors.Accent,
        labelStyle = SaccadenceType.ButtonSecondary.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
        ),
    )
}

/** Outlined destructive action — erase. Danger border and label, weight 700. */
@Composable
fun DestructiveOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = Sizes.buttonHeightSecondary,
) {
    OutlineButtonBase(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = height,
        borderWidth = Sizes.borderWidth,
        borderColor = SaccadenceColors.DangerBorder,
        labelColor = SaccadenceColors.Danger,
        labelStyle = SaccadenceType.ButtonSecondary.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
        ),
    )
}

/**
 * A low-emphasis text-only affordance — the "← Consent" back link, "Scan QR
 * instead". Deliberately not a button shape: the handoff uses a bare 14 px
 * label for these so they never compete with the action stack.
 */
@Composable
fun TextAffordance(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SaccadenceColors.Accent,
    style: TextStyle = SaccadenceType.ButtonSecondarySmall,
) {
    Text(
        text = label,
        style = style,
        color = color,
        modifier = modifier
            .clip(Radius.chip)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
    )
}

@Composable
private fun OutlineButtonBase(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp,
    borderWidth: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    labelColor: Color,
    labelStyle: TextStyle,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(Radius.button)
            .border(
                BorderStroke(borderWidth, if (enabled) borderColor else SaccadenceColors.Hairline),
                Radius.button,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = if (enabled) labelColor else SaccadenceColors.DisabledLabel,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The handoff's language selector: N equal segments in one row, the selected
 * one filled in accent with a white 700 label, the rest outlined with a
 * weight-600 ink-secondary label.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.smPlus),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.selectorHeight)
                    .clip(Radius.field)
                    .then(
                        if (isSelected) {
                            Modifier.background(SaccadenceColors.Accent)
                        } else {
                            Modifier.border(
                                BorderStroke(Sizes.borderWidth, SaccadenceColors.Border),
                                Radius.field,
                            )
                        }
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = if (isSelected) {
                        SaccadenceType.ButtonSecondary.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                        )
                    } else {
                        SaccadenceType.ButtonSecondary
                    },
                    color = if (isSelected) SaccadenceColors.Surface else SaccadenceColors.InkSecondary,
                )
            }
        }
    }
}

/**
 * A tappable pill — the handoff's RECENT doctor chips. Radius 999, chip
 * surface, Manrope 600/14.
 */
@Composable
fun PillChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(Radius.pill)
            .background(SaccadenceColors.ChipSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = ChipMetrics.pillPaddingH, vertical = ChipMetrics.pillPaddingV),
    ) {
        Text(text = label, style = SaccadenceType.ChipLabel, color = SaccadenceColors.Ink)
    }
}

/** The non-interactive tag treatment — DEMO, and any later status tag. */
@Composable
fun Tag(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = SaccadenceColors.ChipSurface,
    textColor: Color = SaccadenceColors.InkMuted,
    shape: RoundedCornerShape = Radius.tag,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .padding(horizontal = ChipMetrics.tagPaddingH, vertical = ChipMetrics.tagPaddingV),
    ) {
        Text(text = label, style = SaccadenceType.MonoTag, color = textColor)
    }
}
