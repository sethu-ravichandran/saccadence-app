package com.arra.saccadence.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The shell every light screen shares: a 24 px-padded scrolling body and, where
 * one is needed, a pinned footer separated by a 1 px hairline.
 *
 * The footer is pinned rather than scrolled because the handoff treats it as a
 * fixed block — the primary action must never scroll out of reach mid-form.
 * The body scrolls independently so long content (the DPDPA consent notice,
 * the full error budget) still fits a phone.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    bodyPadding: Dp = Spacing.screen,
    bodyArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.lg),
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(SaccadenceColors.Surface)) {
        val bodyModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(bodyPadding)
        Column(modifier = bodyModifier, verticalArrangement = bodyArrangement, content = body)

        if (footer != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaccadenceColors.Surface),
            ) {
                Hairline()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.screen,
                            end = Spacing.screen,
                            top = Spacing.mdPlus,
                            bottom = Spacing.lg,
                        ),
                    verticalArrangement = Arrangement.spacedBy(Sizes.footerGap),
                    content = footer,
                )
            }
        }
    }
}

/** A screen's H1, with the optional sub-line the handoff pairs it with. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = title, style = SaccadenceType.H1, color = SaccadenceColors.Ink)
        if (subtitle != null) {
            Text(text = subtitle, style = SaccadenceType.Body, color = SaccadenceColors.InkMuted)
        }
    }
}

/** A mono eyebrow above a group — "RECENT", "VALIDITY". */
@Composable
fun MonoEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SaccadenceColors.InkMutedMono,
) {
    Text(text = text, style = SaccadenceType.MonoEyebrow, color = color, modifier = modifier)
}

/** The 1 px separator used between rows and above footers. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = SaccadenceColors.Hairline,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.borderWidth)
            .background(color),
    )
}

/**
 * The 1 px **dashed** rule the handoff puts above the demo entry point. The
 * dashes are the whole point: they mark the row below as a different kind of
 * thing from the action stack above it, so it never reads as a button.
 */
@Composable
fun DashedRule(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(Sizes.borderWidth)) {
        drawLine(
            color = SaccadenceColors.BorderDashed,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/**
 * A consent / information row: a 700/15 title over a 14 px explanation, with
 * 15 px vertical padding and a hairline underneath.
 */
@Composable
fun InfoRow(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(text = title, style = SaccadenceType.RowTitle, color = SaccadenceColors.Ink)
            Text(text = body, style = SaccadenceType.BodySecondary, color = SaccadenceColors.InkMuted)
        }
        Hairline()
    }
}

/**
 * A checklist row: a 22 px square checkbox, a 12 px gap, then a 700/15 title
 * over an optional 13 px explainer, with a hairline underneath.
 *
 * The whole row is the hit target, not just the box — a 22 px box alone is
 * under the platform's minimum touch size.
 */
@Composable
fun CheckRow(
    checked: Boolean,
    title: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    explainer: String? = null,
    showHairline: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SquareCheckbox(checked = checked)
            Spacer(Modifier.width(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(text = title, style = SaccadenceType.RowTitle, color = SaccadenceColors.Ink)
                if (explainer != null) {
                    Text(
                        text = explainer,
                        style = SaccadenceType.BodySmall,
                        color = SaccadenceColors.InkMutedStrong,
                    )
                }
            }
        }
        if (showHairline) Hairline()
    }
}

/**
 * The handoff's checkbox: 22 × 22, radius 6, a 1.5 px border when unchecked and
 * an accent fill with a white 13 px check when checked. Drawn rather than using
 * Material's `Checkbox`, whose 48 px touch box and 2 px stroke would break the
 * row rhythm.
 */
@Composable
fun SquareCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(Sizes.checkbox)
            .clip(shape)
            .background(if (checked) SaccadenceColors.Accent else SaccadenceColors.Surface)
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier.border(Sizes.borderWidthEmphasis, SaccadenceColors.Border, shape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            // The check is drawn, not set as a "✓" glyph: it is an icon, so it
            // must not grow with the user's font scale and push the fixed 22 px
            // box out of shape. Drawing it also sidesteps the risk of the tick
            // falling outside the bundled font's glyph coverage.
            Canvas(modifier = Modifier.size(13.dp)) {
                val tick = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.54f)
                    lineTo(size.width * 0.40f, size.height * 0.78f)
                    lineTo(size.width * 0.87f, size.height * 0.22f)
                }
                drawPath(
                    path = tick,
                    color = SaccadenceColors.Surface,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

/**
 * The centred home-indicator pill. Present because the handoff specifies it,
 * but only meaningful on a device without a system gesture bar — on a modern
 * Android phone `safeDrawingPadding` already reserves that space and the
 * system draws its own, so this is here for completeness rather than for use.
 */
@Composable
fun HomeIndicator(
    modifier: Modifier = Modifier,
    color: Color = SaccadenceColors.Hairline,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(4.dp)
                .background(color, Radius.tag),
        )
    }
}

/** A horizontal row of equal-width children with the handoff's 10 px gap. */
@Composable
fun EqualRow(
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.smPlus,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}
