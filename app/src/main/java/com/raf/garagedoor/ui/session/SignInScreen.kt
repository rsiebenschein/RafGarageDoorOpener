package com.raf.garagedoor.ui.session

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.garagedoor.R
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.ui.door.GarageDoorIllustration

@Composable
fun SignInRoute(viewModel: SessionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.signInUrls.collect { url -> openInCustomTab(context, url) }
    }
    SignInScreen(state = state, onSignIn = viewModel::startSignIn)
}

@Composable
fun SignInScreen(state: SessionUiState, onSignIn: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SignInIntroduction()
            Spacer(Modifier.height(32.dp))
            SignInAction(state, onSignIn)
            state.errorMessage?.let { SignInError(it) }
        }
    }
}

@Composable
private fun SignInIntroduction() {
    GarageDoorIllustration(state = DoorState.CLOSED, modifier = Modifier.size(160.dp))
    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.sign_in_body),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.sign_in_chooser_hint),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SignInAction(state: SessionUiState, onSignIn: () -> Unit) {
    if (state.isCompletingSignIn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.sign_in_completing))
        }
        return
    }
    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.sign_in_button))
    }
}

@Composable
private fun SignInError(message: String) {
    Spacer(Modifier.height(16.dp))
    Text(text = message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
}

private fun openInCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
}
