package com.raf.garagedoor.ui.door

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raf.garagedoor.R
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.ui.theme.LocalGaragePalette
import com.raf.garagedoor.ui.theme.RafsGarageTheme
import java.time.Duration
import java.time.Instant

@Composable
fun DoorCard(door: GarageDoor, isCommandPending: Boolean, now: Instant, onToggle: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DoorHeader(door)
            GarageDoorIllustration(state = door.state, modifier = Modifier.size(200.dp))
            DoorStatus(door, now)
            DoorCommandButton(door, isCommandPending, onToggle)
        }
    }
}

@Composable
private fun DoorHeader(door: GarageDoor) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(door.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        val palette = LocalGaragePalette.current
        val dotColor = if (door.isOnline) palette.closed else palette.unknown
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (door.isOnline) R.string.door_online else R.string.door_offline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoorStatus(door: GarageDoor, now: Instant) {
    val stateLabel = stringResource(stateLabelFor(door.state))
    val elapsed = door.lastChanged?.let { elapsedLabel(Duration.between(it, now)) }
    Text(
        text = elapsed?.let { stringResource(R.string.door_state_with_elapsed, stateLabel, it) } ?: stateLabel,
        style = MaterialTheme.typography.headlineSmall,
        color = statusColorFor(door.state),
    )
}

@Composable
private fun DoorCommandButton(door: GarageDoor, isCommandPending: Boolean, onToggle: () -> Unit) {
    val command = DoorViewModel.commandFor(door)
    Button(
        onClick = onToggle,
        enabled = command != null && !isCommandPending,
        shape = CircleShape,
        modifier = Modifier.size(128.dp),
    ) {
        if (isCommandPending) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            Text(stringResource(commandLabelFor(command, door)), style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun commandLabelFor(command: DoorCommand?, door: GarageDoor): Int = when {
    command == DoorCommand.OPEN -> R.string.door_command_open
    command == DoorCommand.CLOSE -> R.string.door_command_close
    !door.isOnline -> R.string.door_offline
    door.state.isMoving -> R.string.door_command_moving
    else -> stateLabelFor(door.state)
}

private fun stateLabelFor(state: DoorState): Int = when (state) {
    DoorState.OPEN -> R.string.door_state_open
    DoorState.CLOSED -> R.string.door_state_closed
    DoorState.OPENING -> R.string.door_state_opening
    DoorState.CLOSING -> R.string.door_state_closing
    DoorState.STOPPED -> R.string.door_state_stopped
    DoorState.MOVING -> R.string.door_state_moving
    DoorState.UNKNOWN -> R.string.door_state_unknown
}

@Composable
private fun statusColorFor(state: DoorState): Color {
    val palette = LocalGaragePalette.current
    return when {
        state == DoorState.CLOSED -> palette.closed
        state.isMoving -> palette.moving
        state == DoorState.OPEN || state == DoorState.STOPPED -> palette.open
        else -> palette.unknown
    }
}

@Composable
private fun elapsedLabel(elapsed: Duration): String = when {
    elapsed.toMinutes() < 1 -> stringResource(R.string.elapsed_just_now)
    elapsed.toHours() < 1 -> stringResource(R.string.elapsed_minutes, elapsed.toMinutes())
    elapsed.toDays() < 1 -> stringResource(R.string.elapsed_hours, elapsed.toHours())
    else -> stringResource(R.string.elapsed_days, elapsed.toDays())
}

@Preview(showBackground = true)
@Composable
private fun DoorCardPreview() {
    val now = Instant.now()
    val door = GarageDoor(
        id = DoorId("account", "CG0000000000"),
        name = "Garage Door",
        model = "virtualgaragedooropener",
        state = DoorState.CLOSED,
        isOnline = true,
        lastChanged = now.minus(Duration.ofMinutes(42)),
    )
    RafsGarageTheme { DoorCard(door, isCommandPending = false, now = now, onToggle = {}) }
}
