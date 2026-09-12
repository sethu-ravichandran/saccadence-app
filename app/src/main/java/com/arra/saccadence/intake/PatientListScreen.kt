package com.arra.saccadence.intake

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arra.saccadence.ui.components.DestructiveOutlineButton
import com.arra.saccadence.ui.components.FooterCaption
import com.arra.saccadence.ui.components.Hairline
import com.arra.saccadence.ui.components.ScreenTitle
import com.arra.saccadence.ui.components.TextAffordance
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one screen a DPDP-minded juror will actually ask to see: a real, working
 * per-patient delete, not a described-but-unbuilt right.
 *
 * The handoff's treatment for this screen is a flat, hairline-separated list
 * rather than a stack of cards — a record is a row of facts, and a card around
 * each one implies an object with more behind it than there is.
 *
 * Both halves of the erasure right live here: per-row Delete, and the
 * handoff's bulk "Erase all records". Both confirm first, because neither is
 * recoverable.
 */
@Composable
fun PatientListScreen(
    repository: PatientRepository,
    onBack: () -> Unit,
    onLoadPatient: (PatientRecord) -> Unit,
) {
    var records by remember { mutableStateOf(repository.loadAll().sortedByDescending { it.savedAt }) }
    var pendingDelete by remember { mutableStateOf<PatientRecord?>(null) }
    var confirmEraseAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(SaccadenceColors.Surface)) {
        // Header block, with a bottom border — the handoff separates the
        // header from the rows with a rule rather than with whitespace, so the
        // list reads as a table under a title.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.screen,
                    bottom = Spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TextAffordance(label = "← Close", onClick = onBack)
            ScreenTitle(title = "Saved patients")
        }
        Hairline()

        if (records.isEmpty()) {
            Text(
                text = "No saved patients yet.",
                style = SaccadenceType.BodySecondary,
                color = SaccadenceColors.InkMuted,
                modifier = Modifier.padding(Spacing.screen),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(records, key = { it.id }) { record ->
                PatientRow(
                    record = record,
                    onOpen = { onLoadPatient(record) },
                    onDelete = { pendingDelete = record },
                )
            }
        }

        if (records.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                ) {
                    DestructiveOutlineButton(
                        label = "Erase all records",
                        onClick = { confirmEraseAll = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FooterCaption("Erasing is immediate and cannot be undone.")
                }
            }
        }
    }

    if (confirmEraseAll) {
        AlertDialog(
            onDismissRequest = { confirmEraseAll = false },
            title = {
                Text(
                    "Erase all ${records.size} records?",
                    style = SaccadenceType.H2.copy(fontSize = SaccadenceType.ListTitle.fontSize),
                    color = SaccadenceColors.Ink,
                )
            },
            text = {
                Text(
                    "This erases every saved patient's details and measurements from this device. This cannot be undone.",
                    style = SaccadenceType.BodySecondary,
                    color = SaccadenceColors.InkMuted,
                )
            },
            containerColor = SaccadenceColors.Surface,
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteAll()
                    records = emptyList()
                    confirmEraseAll = false
                }) {
                    Text("Erase all", style = SaccadenceType.ButtonSecondary, color = SaccadenceColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEraseAll = false }) {
                    Text("Cancel", style = SaccadenceType.ButtonSecondary, color = SaccadenceColors.InkSecondary)
                }
            },
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    "Delete this patient?",
                    style = SaccadenceType.H2.copy(fontSize = SaccadenceType.ListTitle.fontSize),
                    color = SaccadenceColors.Ink,
                )
            },
            text = {
                Text(
                    "This erases ${record.name.ifBlank { "this patient" }}'s session, details, and measurements. This cannot be undone.",
                    style = SaccadenceType.BodySecondary,
                    color = SaccadenceColors.InkMuted,
                )
            },
            containerColor = SaccadenceColors.Surface,
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(record.id)
                    records = repository.loadAll().sortedByDescending { it.savedAt }
                    pendingDelete = null
                }) {
                    Text("Delete", style = SaccadenceType.ButtonSecondary, color = SaccadenceColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", style = SaccadenceType.ButtonSecondary, color = SaccadenceColors.InkSecondary)
                }
            },
        )
    }
}

/**
 * One record: a 700/16 name over a mono metadata line, with the trailing
 * "Open" affordance in accent and Delete beside it. The metadata is mono
 * because it is all measured quantities — an age, a date.
 */
@Composable
private fun PatientRow(
    record: PatientRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = record.name.ifBlank { "(no name)" },
                    style = SaccadenceType.ListTitle,
                    color = SaccadenceColors.Ink,
                )
                Text(
                    text = "Age ${record.age ?: "?"} · Dr. ${record.doctorName.ifBlank { "?" }} · " +
                        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(record.savedAt)),
                    style = SaccadenceType.MonoMeta,
                    color = SaccadenceColors.InkMutedMono,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Open",
                style = SaccadenceType.RowAction,
                color = SaccadenceColors.Accent,
            )
            Spacer(Modifier.width(Spacing.lg))
            // Fixed width so the trailing "Open" label lands on one column
            // down the whole list rather than shifting with each name.
            DestructiveOutlineButton(
                label = "Delete",
                onClick = onDelete,
                height = Sizes.buttonHeightCompact,
                modifier = Modifier.width(88.dp),
            )
        }
        Hairline(color = SaccadenceColors.HairlineLight)
    }
}
