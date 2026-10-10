package com.raf.garagedoor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.garagedoor.ui.door.DoorRoute
import com.raf.garagedoor.ui.session.SessionStatus
import com.raf.garagedoor.ui.session.SessionViewModel
import com.raf.garagedoor.ui.session.SignInRoute

@Composable
fun GarageAppRoot(sessionViewModel: SessionViewModel) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    when (session.status) {
        SessionStatus.CHECKING -> SessionCheckingScreen()
        SessionStatus.SIGNED_OUT -> SignInRoute(sessionViewModel)
        SessionStatus.SIGNED_IN -> DoorRoute(onSignOut = sessionViewModel::signOut)
    }
}

@Composable
private fun SessionCheckingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
