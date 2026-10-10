package com.raf.garagedoor.automation

import com.raf.garagedoor.automation.leftopen.LeftOpenMonitor
import com.raf.garagedoor.automation.leftopen.LeftOpenOutcome
import com.raf.garagedoor.automation.leftopen.LeftOpenState
import com.raf.garagedoor.automation.leftopen.LeftOpenStateRepository
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.door.TransientRetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class LeftOpenMonitorTest {

    private var now = Instant.parse("2026-10-10T23:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
    private val controller = FakeDoorController()
    private val settings = MutableSettings(AutomationSettings(leftOpenAction = LeftOpenAction.REMIND, leftOpenMinutes = 30))
    private val state = InMemoryLeftOpenState()
    private val log = mutableListOf<AutomationEvent>()
    private val monitor = LeftOpenMonitor(controller, settings, state, RecordingLog(log), SilentDiagnostics(), TransientRetry(), clock)

    @Test
    fun remindsOnlyAfterThresholdSinceFirstSeenOpen() = runTest {
        controller.state = DoorState.OPEN

        assertEquals(LeftOpenOutcome.CheckAgainIn(Duration.ofMinutes(30)), monitor.check())
        now = now.plus(Duration.ofMinutes(31))

        assertEquals(LeftOpenOutcome.Remind(31), monitor.check())
        assertEquals(AutomationEventType.LEFT_OPEN_REMINDED, log.single().type)
    }

    @Test
    fun ignoreSilencesUntilDoorClosesAndOpensAgain() = runTest {
        controller.state = DoorState.OPEN
        monitor.check()
        state.ignoreCurrentSession()
        now = now.plus(Duration.ofHours(2))
        assertEquals(LeftOpenOutcome.None, monitor.check())

        controller.state = DoorState.CLOSED
        monitor.check()
        controller.state = DoorState.OPEN
        monitor.check()
        now = now.plus(Duration.ofMinutes(31))

        assertTrue(monitor.check() is LeftOpenOutcome.Remind)
    }

    @Test
    fun autoCloseModeClosesTheDoorOnceThresholdPasses() = runTest {
        settings.value = settings.value.copy(leftOpenAction = LeftOpenAction.AUTO_CLOSE)
        controller.state = DoorState.OPEN
        monitor.check()
        now = now.plus(Duration.ofMinutes(45))

        assertEquals(LeftOpenOutcome.AutoClosed, monitor.check())
        assertEquals(listOf(DoorCommand.CLOSE), controller.commands)
        assertNull(state.current().firstSeenOpenAt)
    }

    @Test
    fun countsFromMyQsOpenTimeWhenFirstCheckRunsLate() = runTest {
        controller.state = DoorState.OPEN
        controller.lastChanged = now.minus(Duration.ofMinutes(40))

        assertEquals(LeftOpenOutcome.Remind(40), monitor.check())
    }

    @Test
    fun asksForTheNextCheckWhenTheRepeatReminderIsDue() = runTest {
        controller.state = DoorState.OPEN
        controller.lastChanged = now.minus(Duration.ofMinutes(40))
        monitor.check()
        now = now.plus(Duration.ofMinutes(10))

        assertEquals(LeftOpenOutcome.CheckAgainIn(Duration.ofMinutes(20)), monitor.check())
    }

    @Test
    fun offModeNeverTouchesMyQ() = runTest {
        settings.value = settings.value.copy(leftOpenAction = LeftOpenAction.OFF)

        assertEquals(LeftOpenOutcome.None, monitor.check())
        assertEquals(0, controller.listCalls)
    }

    private class FakeDoorController : GarageDoorController {
        var state = DoorState.CLOSED
        var lastChanged: Instant? = null
        var listCalls = 0
        val commands = mutableListOf<DoorCommand>()

        override suspend fun listDoors(): List<GarageDoor> {
            listCalls++
            return listOf(GarageDoor(DoorId("acct", "CG1"), "Garage Door", null, state, isOnline = true, lastChanged = lastChanged))
        }

        override suspend fun execute(door: DoorId, command: DoorCommand) {
            commands += command
        }
    }

    private class MutableSettings(var value: AutomationSettings) : AutomationSettingsRepository {
        override val settings: Flow<AutomationSettings> get() = MutableStateFlow(value)

        override suspend fun current() = value

        override suspend fun update(transform: (AutomationSettings) -> AutomationSettings) {
            value = transform(value)
        }
    }

    private class InMemoryLeftOpenState : LeftOpenStateRepository {
        private var state = LeftOpenState()

        override suspend fun current() = state

        override suspend fun save(state: LeftOpenState) {
            this.state = state
        }

        override suspend fun ignoreCurrentSession() {
            state = state.copy(ignoredSessionStartedAt = state.firstSeenOpenAt)
        }

        override suspend fun clear() {
            state = LeftOpenState()
        }
    }

    private class RecordingLog(private val recorded: MutableList<AutomationEvent>) : ActivityLogRepository {
        override val events: Flow<List<AutomationEvent>> get() = MutableStateFlow(recorded.toList())

        override suspend fun record(event: AutomationEvent) {
            recorded += event
        }

        override suspend fun clear() = recorded.clear()
    }

    private class SilentDiagnostics : DiagnosticsLog {
        override val entries: Flow<List<DiagnosticEntry>> get() = MutableStateFlow(emptyList())

        override suspend fun record(message: String) = Unit

        override suspend fun clear() = Unit
    }
}
