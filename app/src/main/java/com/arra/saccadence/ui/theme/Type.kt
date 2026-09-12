package com.arra.saccadence.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.arra.saccadence.R

/**
 * Manrope, supplied as a single variable font driven on its `wght` axis rather
 * than five static instances — one 165 KB file instead of ~480 KB, and the
 * axis is honoured from API 26 up, which is this app's `minSdk`.
 */
@OptIn(ExperimentalTextApi::class)
private fun manropeWeight(weight: Int) = Font(
    resId = R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** UI text. The handoff uses 400/500/600/700/800. */
val Manrope = FontFamily(
    manropeWeight(400),
    manropeWeight(500),
    manropeWeight(600),
    manropeWeight(700),
    manropeWeight(800),
)

/**
 * Space Mono, for every numeral, code, eyebrow label, and technical readout.
 * The handoff calls this mono/sans split "load-bearing": **anything measured
 * is mono.** Keep it that way — a latency in Manrope reads as prose, not as an
 * instrument reading.
 */
val SpaceMono = FontFamily(
    Font(R.font.space_mono_400, FontWeight.Normal),
    Font(R.font.space_mono_700, FontWeight.Bold),
)

/**
 * The handoff's type scale, one property per named role.
 *
 * Sizes are in `sp` so the clinic can scale text, while the handoff's `px`
 * figures are logical pixels at 1× — they map 1:1. Letter spacing is
 * expressed in `em`, matching the handoff's tracking values directly.
 */
object SaccadenceType {

    /** H1 — screen titles. 30 px / 800 / −0.025em / 1.1. */
    val H1 = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W800,
        fontSize = 30.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.025).em,
    )

    /** H2 — 27 px / 800 / −0.02em. */
    val H2 = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W800,
        fontSize = 27.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.02).em,
    )

    /** The dark stimulus pane's instruction title. 25 px / 800. */
    val InstructionTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W800,
        fontSize = 25.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.02).em,
    )

    /** A headline metric. Mono 700, 44 px / −0.03em / 1.0. */
    val Metric = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.03).em,
    )

    /** The unit suffix beside a [Metric]. 16 px / 600. */
    val MetricUnit = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
    )

    /** A stat-card value. Mono 700, 22 px. */
    val Stat = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    )

    /** The unit suffix beside a [Stat]. 12 px / 400. */
    val StatUnit = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )

    /** Body copy. 15 px / 1.5. */
    val Body = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 22.5.sp,
    )

    /** Secondary body — explainers under a row title. 14 px / 1.45. */
    val BodySecondary = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** A 13 px explanatory line, tighter again. */
    val BodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W400,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Row and list-item titles. 15 px / 700. */
    val RowTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W700,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    /** A patient-list name or section heading. 16 px / 700. */
    val ListTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    /** An emphasised in-body heading. 15 px / 800. */
    val SectionTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W800,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    /** The emphasised audit row ("Total error budget"). 14 px / 800. */
    val AuditEmphasis = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W800,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    )

    /** A field label sitting above its input. 13 px / 700. */
    val FieldLabel = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W700,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )

    /** The word "optional" inside a field label — lighter, fainter. */
    val FieldLabelOptional = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )

    /** Value text inside a text field. 16 px / 400. */
    val FieldValue = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    /** Mono value text inside a field (IP address). 16 px. */
    val FieldValueMono = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    /** Mono value text for a session code — tracked wide. */
    val FieldValueCode = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.em,
    )

    /** Filled-button label. 16 px / 700. */
    val ButtonPrimary = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
    )

    /** Outlined-button label. 15 px / 600. */
    val ButtonSecondary = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
    )

    /** A compact outlined-button label. 14 px / 600. */
    val ButtonSecondarySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
    )

    /** A status-block title. 14 px / 700. */
    val StatusTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W700,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    )

    /** A status line that is reporting rather than asserting. 14 px / 600. */
    val StatusLine = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    )

    /** A footer or under-row caption. 12 px. */
    val Caption = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** A recent-doctor chip label. 14 px / 600. */
    val ChipLabel = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
    )

    /** The trailing "Load" affordance on a patient row. 13 px / 700. */
    val RowAction = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.W700,
        fontSize = 13.sp,
    )

    // ---- Mono eyebrows: 10–11 px with 0.06–0.14em tracking ------------------

    /** Step-bar and section eyebrow. Mono 11 px / 0.08em. */
    val MonoEyebrow = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.08.em,
    )

    /** Status-bar clock and wordmark. Mono 11 px, untracked. */
    val MonoStatusBar = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    )

    /** A mono metadata line under a list name. 12 px. */
    val MonoMeta = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** A stat-card label. Mono 10 px / 0.1em. */
    val MonoStatLabel = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.1.em,
    )

    /** A metric label above a headline number. Mono 10 px / 0.12em. */
    val MonoMetricLabel = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.12.em,
    )

    /** The widest tracking: a dark pane's eyebrow. Mono 10 px / 0.14em. */
    val MonoPaneEyebrow = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.14.em,
    )

    /** A DEMO-style tag. Mono 10 px / 0.06em. */
    val MonoTag = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.06.em,
    )

    /** A chip over live video. Mono 10 px / 0.1em. */
    val MonoVideoChip = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.1.em,
    )

    /** An instructional chip under the camera preview. Mono 11 px. */
    val MonoVideoCaption = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    )

    /** A mono audit/breakdown line. 11 px. */
    val MonoAudit = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )

    /** A mono value in an audit row, emphasised. 11 px / 700. */
    val MonoAuditValue = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )

    /** A mono counter on the right of the quality line. 11 px. */
    val MonoCounter = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    )
}
