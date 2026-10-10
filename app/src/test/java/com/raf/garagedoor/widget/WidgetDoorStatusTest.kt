package com.raf.garagedoor.widget

import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetDoorStatusTest {

    private val now = Instant.parse("2026-10-10T22:00:00Z")
    private val open = WidgetDoorStatus(state = DoorState.OPEN)

    @Test
    fun confirmation_lastsOnlyForTheWindow() {
        val asking = open.awaitingConfirm(DoorCommand.CLOSE, now)

        assertTrue(asking.isAwaitingConfirm(DoorCommand.CLOSE, now.plusSeconds(3)))
        assertFalse(asking.isAwaitingConfirm(DoorCommand.CLOSE, now.plusSeconds(5)))
        assertFalse(asking.isAwaitingConfirm(DoorCommand.OPEN, now.plusSeconds(1)))
    }

    @Test
    fun movingOverlay_survivesMyQStillReportingTheOldState() {
        val closing = open.moving(DoorCommand.CLOSE, now)

        val afterStaleReport = closing.withReported(door(DoorState.OPEN), now.plusSeconds(3))

        assertEquals(DoorState.CLOSING, afterStaleReport.displayState(now.plusSeconds(3)))
    }

    @Test
    fun movingOverlay_clearsOnceMyQReportsTheNewState() {
        val closing = open.moving(DoorCommand.CLOSE, now)

        val afterClosed = closing.withReported(door(DoorState.CLOSED), now.plusSeconds(20))

        assertEquals(DoorState.CLOSED, afterClosed.displayState(now.plusSeconds(20)))
    }

    @Test
    fun movingOverlay_expiresIfMyQNeverCatchesUp() {
        val closing = open.moving(DoorCommand.CLOSE, now)

        assertEquals(DoorState.OPEN, closing.displayState(now.plusSeconds(120)))
    }

    @Test
    fun sendingClearsAnyPendingConfirmation() {
        val sent = open.awaitingConfirm(DoorCommand.CLOSE, now).moving(DoorCommand.CLOSE, now)

        assertFalse(sent.isAwaitingConfirm(DoorCommand.CLOSE, now))
    }

    private fun door(state: DoorState) = GarageDoor(DoorId("acct", "CG1"), "Garage Door", null, state, isOnline = true, lastChanged = null)
}
