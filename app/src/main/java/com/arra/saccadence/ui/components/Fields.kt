package com.arra.saccadence.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The handoff's text field: a 13 px/700 label *above* a 52 px box, 1 px border
 * that becomes 1.5 px accent on focus.
 *
 * Built on [BasicTextField] rather than Material's `OutlinedTextField` because
 * the latter's label is a floating notch inside the border — a different
 * component, not a re-skin of this one. The blinking caret the handoff draws
 * on the focused field comes free: `BasicTextField`'s own cursor, tinted
 * accent, is exactly the 1.5 × 20 px accent caret specified.
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    /** Rendered inside the label at weight 500 and fainter — the handoff's "optional". */
    labelQualifier: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = SaccadenceType.FieldValue,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = Sizes.fieldHeight,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FieldLabel(label = label, qualifier = labelQualifier)
        FieldBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle,
            singleLine = singleLine,
            minHeight = minHeight,
        )
    }
}

/**
 * The multiline notes field: the handoff's 62 px box with 12 px / 14 px
 * padding, otherwise identical to [LabeledTextField].
 */
@Composable
fun NotesField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    labelQualifier: String? = null,
) {
    LabeledTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        labelQualifier = labelQualifier,
        singleLine = false,
        minHeight = Sizes.fieldHeightMultiline,
    )
}

/** The label above a field. Weight 700; any [qualifier] drops to 500 and fainter. */
@Composable
fun FieldLabel(label: String, qualifier: String? = null) {
    val text: AnnotatedString = if (qualifier == null) {
        AnnotatedString(label)
    } else {
        buildAnnotatedString {
            append(label)
            append(" ")
            withStyle(
                SpanStyle(
                    fontWeight = SaccadenceType.FieldLabelOptional.fontWeight,
                    color = SaccadenceColors.InkFaintStrong,
                )
            ) { append(qualifier) }
        }
    }
    Text(text = text, style = SaccadenceType.FieldLabel, color = SaccadenceColors.InkSecondary)
}

@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    keyboardOptions: KeyboardOptions,
    textStyle: TextStyle,
    singleLine: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val selectionColors = TextSelectionColors(
        handleColor = SaccadenceColors.Accent,
        backgroundColor = SaccadenceColors.AccentTint,
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = SaccadenceColors.Ink),
            cursorBrush = SolidColor(SaccadenceColors.Accent),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .border(
                    BorderStroke(
                        width = if (focused) Sizes.borderWidthEmphasis else Sizes.borderWidth,
                        color = if (focused) SaccadenceColors.Accent else SaccadenceColors.BorderField,
                    ),
                    Radius.field,
                ),
            decorationBox = { inner ->
                // A single-line box centres its text in a fixed 52 px height; a
                // multiline one grows from the 62 px minimum and pads top-aligned.
                val boxModifier = if (singleLine) {
                    Modifier.fillMaxWidth().height(minHeight).padding(horizontal = Spacing.mdPlus)
                } else {
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.mdPlus, vertical = Spacing.md)
                }
                Box(
                    modifier = boxModifier,
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = SaccadenceColors.Placeholder,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
