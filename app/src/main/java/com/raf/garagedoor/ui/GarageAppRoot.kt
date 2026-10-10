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
import com.raf.garagedoor.ui.door.DoorRoute
import com.raf.garagedoor.ui.geofence.GeofenceEditorRoute
import com.raf.garagedoor.ui.geofence.GeofenceViewModel
import com.raf.garagedoor.ui.session.SessionStatus
import com.raf.garagedoor.ui.session.SessionViewModel
import com.raf.garagedoor.ui.session.SignInRoute

private enum class SignedInDestination { DOORS, GEOFENCE_EDITOR }

@Composable
fun GarageAppRoot(sessionViewModel: SessionViewModel) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    when (session.status) {
        SessionStatus.CHECKING -> SessionCheckingScreen()
        SessionStatus.SIGNED_OUT -> SignInRoute(sessionViewModel)
        SessionStatus.SIGNED_IN -> SignedInNavigation(onSignOut = sessionViewModel::signOut)
    }
}

/** Two destinations do not justify a navigation library; the back stack is a single saveable value. */
@Composable
private fun SignedInNavigation(onSignOut: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(SignedInDestination.DOORS) }
    val geofenceViewModel: GeofenceViewModel = viewModel(factory = GeofenceViewModel.Factory)
    val geofenceState by geofenceViewModel.state.collectAsStateWithLifecycle()
    when (destination) {
        SignedInDestination.DOORS -> DoorRoute(
            geofence = geofenceState.saved,
            onEditGeofence = { destination = SignedInDestination.GEOFENCE_EDITOR },
            onSignOut = onSignOut,
        )
        SignedInDestination.GEOFENCE_EDITOR -> GeofenceEditorRoute(
            viewModel = geofenceViewModel,
            onBack = { destination = SignedInDestination.DOORS },
        )
    }
}

@Composable
private fun SessionCheckingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
