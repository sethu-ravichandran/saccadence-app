package com.arra.saccadence.intake

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arra.saccadence.ui.components.StepBar

/**
 * "Step X of Y" header shown above every form step. Back is always allowed and
 * never clears state.
 *
 * The design system's [StepBar] carries the whole treatment — the mono
 * "STEP n OF 7" eyebrow, the step name on the right, and the 4 px accent
 * track. This stays as a named wrapper so the intake screens keep reading in
 * their own vocabulary, and so the step total lives in exactly one place.
 */
@Composable
fun StepProgress(step: Int, total: Int = 7, label: String, modifier: Modifier = Modifier) {
    StepBar(step = step, total = total, label = label, modifier = modifier)
}
