package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.arra.saccadence.ui.components.InfoNote
import com.arra.saccadence.ui.components.LabeledTextField
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.ScreenScaffold
import com.arra.saccadence.ui.components.SecondaryButton
import com.arra.saccadence.ui.components.TextAffordance
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

@Composable
fun PatientDetailsScreen(
    session: PatientSession,
    repository: PatientRepository,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var lookupMessage by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        bodyArrangement = Arrangement.spacedBy(Spacing.xl),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Sizes.footerGap),
            ) {
                SecondaryButton(
                    label = "Back",
                    onClick = onBack,
                    modifier = Modifier.width(Sizes.backButtonWidth),
                )
                PrimaryButton(
                    label = "Next",
                    onClick = onNext,
                    enabled = session.isDetailsValid,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        StepProgress(step = 1, label = "Patient details")

        LabeledTextField(
            label = "Full name",
            value = session.name,
            onValueChange = { session.name = it; lookupMessage = null },
            modifier = Modifier.fillMaxWidth(),
        )

        // Nice-to-have: local lookup for a returning patient, only useful once
        // there's history to find — never required to move forward.
        TextAffordance(
            label = "Have we seen this patient before?",
            onClick = {
                val found = repository.findByNameAndAge(session.name, session.age)
                lookupMessage = if (found != null) {
                    session.resetTo(found)
                    "Loaded previous visit for ${found.name}."
                } else {
                    "No previous visit found for this name/age."
                }
            },
        )
        lookupMessage?.let { message ->
            // The handoff's returning-patient note: accent tint, a mono "↺",
            // 13 px accent text. It reports the lookup's outcome either way —
            // "no previous visit" is information, not an error.
            InfoNote(glyph = "↺", text = message)
        }

        // Age and mobile share a row, the handoff's 1 : 1.4 split — a two-digit
        // age next to a ten-digit number wants the width where the digits are.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.mdPlus),
        ) {
            LabeledTextField(
                label = "Age",
                value = session.age?.toString() ?: "",
                onValueChange = { text ->
                    session.age = text.filter { it.isDigit() }.take(3).toIntOrNull()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = SaccadenceType.FieldValueMono,
                modifier = Modifier.weight(1f),
            )
            LabeledTextField(
                label = "Mobile",
                labelQualifier = "optional",
                value = session.mobile,
                onValueChange = { session.mobile = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = SaccadenceType.FieldValueMono,
                modifier = Modifier.weight(1.4f),
            )
        }

        Spacer(Modifier.weight(1f, fill = false))
    }
}
