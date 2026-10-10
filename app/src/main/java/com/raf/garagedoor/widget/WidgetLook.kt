package com.raf.garagedoor.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.raf.garagedoor.R
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Labels and color for one rendering of the widget, so the layouts stay free of state logic. */
data class WidgetLook(val color: Color, val shortLabel: String, val detailLabel: String, val actionLabel: String) {

    companion object {
        fun of(context: Context, status: WidgetDoorStatus, now: Instant): WidgetLook {
            val state = status.displayState(now)
            val command = state?.toggleCommand
            val isConfirming = command != null && status.isAwaitingConfirm(command, now)
            val tapAgain = context.getString(R.string.widget_tap_again)
            return WidgetLook(
                color = colorFor(state),
                shortLabel = if (isConfirming) tapAgain else stateLabel(context, state),
                detailLabel = detailLabel(context, state, status.lastChanged),
                actionLabel = if (isConfirming) tapAgain else actionLabel(context, state, command),
            )
        }

        private fun stateLabel(context: Context, state: DoorState?): String = when (state) {
            null -> context.getString(R.string.widget_state_unknown)
            DoorState.OPEN -> context.getString(R.string.door_state_open)
            DoorState.CLOSED -> context.getString(R.string.door_state_closed)
            DoorState.OPENING -> context.getString(R.string.widget_state_moving, context.getString(R.string.door_state_opening))
            DoorState.CLOSING -> context.getString(R.string.widget_state_moving, context.getString(R.string.door_state_closing))
            DoorState.MOVING -> context.getString(R.string.widget_state_moving, context.getString(R.string.door_state_moving))
            DoorState.STOPPED -> context.getString(R.string.door_state_stopped)
            DoorState.UNKNOWN -> context.getString(R.string.door_state_unknown)
        }

        private fun detailLabel(context: Context, state: DoorState?, lastChanged: Instant?): String {
            val label = stateLabel(context, state)
            if (state == null || state.isMoving || lastChanged == null) return label
            return context.getString(R.string.widget_state_since, label, sinceLabel(lastChanged))
        }

        private fun sinceLabel(lastChanged: Instant): String {
            val local = lastChanged.atZone(ZoneId.systemDefault())
            val isToday = local.toLocalDate() == LocalDate.now()
            return local.format(if (isToday) TIME_FORMAT else DATE_FORMAT)
        }

        private fun actionLabel(context: Context, state: DoorState?, command: DoorCommand?): String = when {
            state == null || state == DoorState.UNKNOWN -> context.getString(R.string.widget_action_update)
            command == DoorCommand.OPEN -> context.getString(R.string.widget_action_open)
            command == DoorCommand.CLOSE -> context.getString(R.string.widget_action_close)
            else -> context.getString(R.string.widget_action_wait)
        }

        private fun colorFor(state: DoorState?): Color = when {
            state == DoorState.CLOSED -> CLOSED_COLOR
            state == DoorState.OPEN || state == DoorState.STOPPED -> OPEN_COLOR
            state?.isMoving == true -> MOVING_COLOR
            else -> UNKNOWN_COLOR
        }

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
        private val CLOSED_COLOR = Color(0xFF6FD39A)
        private val OPEN_COLOR = Color(0xFFFFC266)
        private val MOVING_COLOR = Color(0xFF7DB5F5)
        private val UNKNOWN_COLOR = Color(0xFF9AA6B4)
    }
}
