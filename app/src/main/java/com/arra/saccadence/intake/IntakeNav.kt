package com.arra.saccadence.intake

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object IntakeRoutes {
    const val CONSENT = "consent"
    const val DETAILS = "details"
    const val DOCTOR = "doctor"
    const val SYMPTOMS = "symptoms"
    const val PATIENTS = "patients"
}

/** "Step X of 4" header shown above every form step. Back is always allowed and never clears state. */
@Composable
fun StepProgress(step: Int, total: Int = 4, label: String) {
    Text(
        text = "Step $step of $total — $label",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    LinearProgressIndicator(
        progress = { step / total.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    )
}

/**
 * One NavHost, one route per step, [session] hoisted above it all so moving back
 * and forth never loses input. [onReachCalibration] hands the completed session
 * off to Sethu's calibration/recording screens and gets out of the way.
 */
@Composable
fun IntakeNavHost(
    session: PatientSession,
    repository: PatientRepository,
    onReachCalibration: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = IntakeRoutes.CONSENT) {
        composable(IntakeRoutes.CONSENT) {
            ConsentScreen(
                session = session,
                onConsented = { navController.navigate(IntakeRoutes.DETAILS) },
                onDemoPatient = {
                    session.fillDemoData()
                    onReachCalibration()
                },
                onOpenPatients = { navController.navigate(IntakeRoutes.PATIENTS) },
            )
        }
        composable(IntakeRoutes.DETAILS) {
            PatientDetailsScreen(
                session = session,
                repository = repository,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(IntakeRoutes.DOCTOR) },
            )
        }
        composable(IntakeRoutes.DOCTOR) {
            DoctorScreen(
                session = session,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(IntakeRoutes.SYMPTOMS) },
            )
        }
        composable(IntakeRoutes.SYMPTOMS) {
            SymptomsScreen(
                session = session,
                repository = repository,
                onBack = { navController.popBackStack() },
                onNext = {
                    repository.save(session.toRecord())
                    onReachCalibration()
                },
            )
        }
        composable(IntakeRoutes.PATIENTS) {
            PatientListScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onLoadPatient = { record ->
                    session.resetTo(record)
                    navController.popBackStack(IntakeRoutes.CONSENT, inclusive = false)
                    navController.navigate(IntakeRoutes.DETAILS)
                },
            )
        }
    }
}
