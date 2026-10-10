package com.raf.garagedoor.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.raf.garagedoor.door.DoorCommandEvent
import com.raf.garagedoor.door.GarageDoor
import java.time.Clock

/** Feeds every myQ status and accepted command into the widget, redrawing only when something changed. */
class WidgetUpdater(private val context: Context, private val store: WidgetDoorStatusStore, private val clock: Clock) {

    suspend fun onDoorsListed(doors: List<GarageDoor>) {
        val door = doors.firstOrNull() ?: return
        if (store.update { it.withReported(door, clock.instant()) }) redraw()
    }

    /** Any command, from any sender, shows as moving and is followed until myQ reports it done. */
    suspend fun onCommandAccepted(event: DoorCommandEvent) {
        if (store.update { it.moving(event.command, clock.instant()) }) redraw()
        DoorStatusWorker.enqueue(context, follow = event.command)
    }

    fun requestRefresh() = DoorStatusWorker.enqueue(context, follow = null)

    suspend fun redraw() = GarageDoorWidget().updateAll(context)
}
