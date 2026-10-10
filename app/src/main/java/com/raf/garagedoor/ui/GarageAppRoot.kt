package com.raf.garagedoor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raf.garagedoor.ui.automation.ActivityLogRoute
import com.raf.garagedoor.ui.automation.AutomationSettingsRoute
import com.raf.garagedoor.ui.automation.AutomationViewModel
import com.raf.garagedoor.ui.automation.DiagnosticsRoute
import com.raf.garagedoor.ui.door.AutomationCardState
import com.raf.garagedoor.ui.door.DoorRoute
import com.raf.garagedoor.ui.geofence.GeofenceEditorRoute
import com.raf.garagedoor.ui.geofence.GeofenceViewModel
import com.raf.garagedoor.ui.session.SessionStatus
import com.raf.garagedoor.ui.session.SessionViewModel
import com.raf.garagedoor.ui.session.SignInRoute

private enum class SignedInDestination { DOORS, GEOFENCE_EDITOR, AUTOMATION_SETTINGS, ACTIVITY_LOG, DIAGNOSTICS }

@Composable
fun GarageAppRoot(sessionViewModel: SessionViewModel) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    when (session.status) {
        SessionStatus.CHECKING -> SessionCheckingScreen()
        SessionStatus.SIGNED_OUT -> SignInRoute(sessionViewModel)
        SessionStatus.SIGNED_IN -> SignedInNavigation(onSignOut = sessionViewModel::signOut)
    }
}

/** A handful of flat destinations does not justify a navigation library; the back stack is one saveable value. */
@Composable
private fun SignedInNavigation(onSignOut: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(SignedInDestination.DOORS) }
    val geofenceViewModel: GeofenceViewModel = viewModel(factory = GeofenceViewModel.Factory)
    val automationViewModel: AutomationViewModel = viewModel(factory = AutomationViewModel.Factory)
    val navigate: (SignedInDestination) -> Unit = { destination = it }
    when (destination) {
        SignedInDestination.DOORS -> DoorDestination(geofenceViewModel, automationViewModel, navigate, onSignOut)
        SignedInDestination.GEOFENCE_EDITOR -> GeofenceEditorRoute(geofenceViewModel, onBack = { navigate(SignedInDestination.DOORS) })
        SignedInDestination.AUTOMATION_SETTINGS -> AutomationSettingsRoute(
            viewModel = automationViewModel,
            onBack = { navigate(SignedInDestination.DOORS) },
            onEditLocation = { navigate(SignedInDestination.GEOFENCE_EDITOR) },
            onOpenActivityLog = { navigate(SignedInDestination.ACTIVITY_LOG) },
            onOpenDiagnostics = { navigate(SignedInDestination.DIAGNOSTICS) },
        )
        SignedInDestination.ACTIVITY_LOG -> ActivityLogRoute(automationViewModel, onBack = { navigate(SignedInDestination.AUTOMATION_SETTINGS) })
        SignedInDestination.DIAGNOSTICS -> DiagnosticsRoute(automationViewModel, onBack = { navigate(SignedInDestination.AUTOMATION_SETTINGS) })
    }
}

@Composable
private fun DoorDestination(
    geofenceViewModel: GeofenceViewModel,
    automationViewModel: AutomationViewModel,
    navigate: (SignedInDestination) -> Unit,
    onSignOut: () -> Unit,
) {
    val geofenceState by geofenceViewModel.state.collectAsStateWithLifecycle()
    val automationState by automationViewModel.state.collectAsStateWithLifecycle()
    val automationCard = AutomationCardState(
        geofence = geofenceState.saved,
        isEnabled = automationState?.settings?.isEnabled == true,
        onEditLocation = { navigate(SignedInDestination.GEOFENCE_EDITOR) },
        onOpenSettings = { navigate(SignedInDestination.AUTOMATION_SETTINGS) },
    )
    DoorRoute(automationCard = automationCard, onSignOut = onSignOut)
}

@Composable
private fun SessionCheckingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
