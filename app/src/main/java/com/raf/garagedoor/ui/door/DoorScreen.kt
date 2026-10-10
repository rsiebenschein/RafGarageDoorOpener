package com.raf.garagedoor.ui.door

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raf.garagedoor.R
import com.raf.garagedoor.door.GarageDoor
import kotlinx.coroutines.delay
import java.time.Instant

private const val ELAPSED_LABEL_REFRESH_MILLIS = 30_000L

@Composable
fun DoorRoute(onSignOut: () -> Unit, viewModel: DoorViewModel = viewModel(factory = DoorViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.startPolling()
        onPauseOrDispose { viewModel.stopPolling() }
    }
    DoorScreen(
        state = state,
        onToggle = viewModel::toggle,
        onRefresh = viewModel::refreshNow,
        onDismissError = viewModel::dismissError,
        onSignOut = onSignOut,
    )
}

@Composable
fun DoorScreen(
    state: DoorUiState,
    onToggle: (GarageDoor) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onSignOut: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissError()
    }
    Scaffold(
        topBar = { DoorTopBar(onRefresh = onRefresh, onSignOut = onSignOut) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        DoorContent(state = state, onToggle = onToggle, modifier = Modifier.padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoorTopBar(onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var isMenuOpen by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
            IconButton(onClick = { isMenuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_sign_out)) },
                    onClick = { isMenuOpen = false; onSignOut() },
                )
            }
        },
    )
}

@Composable
private fun DoorContent(state: DoorUiState, onToggle: (GarageDoor) -> Unit, modifier: Modifier) {
    val now by rememberTickingNow()
    when {
        state.isLoading && state.doors.isEmpty() -> CenteredBox(modifier) { CircularProgressIndicator() }
        state.doors.isEmpty() -> CenteredBox(modifier) {
            Text(stringResource(R.string.doors_empty), textAlign = TextAlign.Center)
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.doors, key = { it.id.serialNumber }) { door ->
                DoorCard(door, isCommandPending = state.pendingCommandDoor == door.id, now = now) { onToggle(door) }
            }
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun rememberTickingNow() = produceState(initialValue = Instant.now()) {
    while (true) {
        delay(ELAPSED_LABEL_REFRESH_MILLIS)
        value = Instant.now()
    }
}
