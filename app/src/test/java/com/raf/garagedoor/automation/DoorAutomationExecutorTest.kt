package com.raf.garagedoor.automation

import com.raf.garagedoor.bluetooth.CarConnectionRepository
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.door.TransientRetry
import com.raf.garagedoor.myq.MyQNetworkException
import com.raf.garagedoor.myq.MyQRateLimitedException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.io.IOException
import java.time.ZoneOffset

class DoorAutomationExecutorTest {

    private val now = Instant.parse("2026-10-10T18:00:00Z")
    private val doorId = DoorId("acct", "CG1")
    private val controller = FakeController()
    private val settings = FakeSettings(AutomationSettings(isEnabled = true))
    private val presence = FakePresence()
    private val car = FakeCarConnection()
    private val log = FakeActivityLog()
    private val alerts = RecordingAlerts()
    private val executor = DoorAutomationExecutor(
        controller, settings, presence, car, log, alerts, CommandCountdown(), TransientRetry(), FakeDiagnostics(), Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun open_sendsCommandWhenDoorClosedAndRecordsAction() = runTest {
        controller.state = DoorState.CLOSED

        executor.execute(DoorDecision.OPEN, distanceMeters = 20.0)

        assertEquals(listOf(DoorCommand.OPEN), controller.commands)
        assertEquals(AutomationEventType.OPENED, log.recorded.single().type)
        assertEquals(66, log.recorded.single().distanceFeet)
        assertEquals(now, presence.lastAction)
        assertEquals(listOf(AutomationEventType.OPENED), alerts.results)
    }

    @Test
    fun close_onAlreadyClosedDoorDoesNothingButLogs() = runTest {
        controller.state = DoorState.CLOSED

        executor.execute(DoorDecision.CLOSE, distanceMeters = 60.0)

        assertTrue(controller.commands.isEmpty())
        assertEquals(AutomationEventType.ALREADY_CLOSED, log.recorded.single().type)
        assertTrue(alerts.results.isEmpty())
    }

    @Test
    fun open_requiresSelectedCarWhenSettingIsOn() = runTest {
        controller.state = DoorState.CLOSED
        settings.value = AutomationSettings(isEnabled = true, requireCarBluetooth = true, car = CarBluetoothDevice("AA:BB", "Car"))

        executor.execute(DoorDecision.OPEN, distanceMeters = 20.0)
        car.connected += "AA:BB"
        executor.execute(DoorDecision.OPEN, distanceMeters = 20.0)

        assertEquals(listOf(AutomationEventType.SKIPPED_CAR_NOT_CONNECTED, AutomationEventType.OPENED), log.recorded.map { it.type })
        assertEquals(listOf(DoorCommand.OPEN), controller.commands)
    }

    @Test
    fun close_isNotBlockedByCarRequirement() = runTest {
        controller.state = DoorState.OPEN
        settings.value = AutomationSettings(isEnabled = true, requireCarBluetooth = true, car = CarBluetoothDevice("AA:BB", "Car"))

        executor.execute(DoorDecision.CLOSE, distanceMeters = 60.0)

        assertEquals(listOf(DoorCommand.CLOSE), controller.commands)
    }

    @Test
    fun cooldown_blocksSecondActionWithinNinetySeconds() = runTest {
        controller.state = DoorState.OPEN
        presence.lastAction = now.minusSeconds(30)

        executor.execute(DoorDecision.CLOSE, distanceMeters = 60.0)

        assertTrue(controller.commands.isEmpty())
        assertEquals(AutomationEventType.SKIPPED_COOLDOWN, log.recorded.single().type)
    }

    @Test
    fun cooldown_doesNotBlockOpeningOnAQuickReturn() = runTest {
        controller.state = DoorState.CLOSED
        presence.lastAction = now.minusSeconds(30)

        executor.execute(DoorDecision.OPEN, distanceMeters = 20.0)

        assertEquals(listOf(DoorCommand.OPEN), controller.commands)
    }

    @Test
    fun disabled_doesNothingAtAll() = runTest {
        settings.value = AutomationSettings(isEnabled = false)

        executor.execute(DoorDecision.OPEN, distanceMeters = 20.0)

        assertTrue(controller.commands.isEmpty() && log.recorded.isEmpty())
    }

    @Test
    fun controllerFailure_isLoggedAndNotified() = runTest {
        controller.state = DoorState.CLOSED
        controller.failures += MyQRateLimitedException()

        executor.execute(DoorDecision.OPEN, distanceMeters = 20.0)

        assertEquals(AutomationEventType.FAILED, log.recorded.single().type)
        assertEquals(listOf(AutomationEventType.FAILED), alerts.results)
        assertEquals(listOf(DoorCommand.OPEN), alerts.retryCommands)
    }

    @Test
    fun networkDropDuringHandoff_isRetriedAndSucceeds() = runTest {
        controller.state = DoorState.OPEN
        controller.failures += MyQNetworkException(IOException("timeout"))

        executor.execute(DoorDecision.CLOSE, distanceMeters = 160.0)

        assertEquals(listOf(DoorCommand.CLOSE), controller.commands)
        assertEquals(AutomationEventType.CLOSED, log.recorded.single().type)
    }

    private inner class FakeController : GarageDoorController {
        var state = DoorState.CLOSED
        val failures = ArrayDeque<GarageDoorControlException>()
        val commands = mutableListOf<DoorCommand>()

        override suspend fun listDoors() = listOf(GarageDoor(doorId, "Garage Door", null, state, isOnline = true, lastChanged = null))

        override suspend fun execute(door: DoorId, command: DoorCommand) {
            failures.removeFirstOrNull()?.let { throw it }
            commands += command
        }
    }

    private class FakeSettings(var value: AutomationSettings) : AutomationSettingsRepository {
        override val settings: Flow<AutomationSettings> get() = MutableStateFlow(value)

        override suspend fun current() = value

        override suspend fun update(transform: (AutomationSettings) -> AutomationSettings) {
            value = transform(value)
        }
    }

    private class FakePresence : PresenceRepository {
        var lastAction: Instant? = null
        override val state: Flow<PresenceState> get() = MutableStateFlow(PresenceState(lastActionAt = lastAction))

        override suspend fun current() = PresenceState(lastActionAt = lastAction)

        override suspend fun save(state: PresenceState) {
            lastAction = state.lastActionAt
        }

        override suspend fun recordAction(at: Instant) {
            lastAction = at
        }

        override suspend fun registeredFingerprint(): String? = null

        override suspend fun saveRegisteredFingerprint(fingerprint: String?) = Unit
    }

    private class FakeCarConnection : CarConnectionRepository {
        val connected = mutableSetOf<String>()
        override val connectedAddresses: Flow<Set<String>> get() = MutableStateFlow(connected.toSet())

        override suspend fun isConnected(address: String) = address in connected

        override suspend fun onConnectionChanged(address: String, isConnected: Boolean) {
            if (isConnected) connected += address else connected -= address
        }

        override suspend fun clear() = connected.clear()
    }

    private class FakeActivityLog : ActivityLogRepository {
        val recorded = mutableListOf<AutomationEvent>()
        override val events: Flow<List<AutomationEvent>> get() = MutableStateFlow(recorded.toList())

        override suspend fun record(event: AutomationEvent) {
            recorded += event
        }

        override suspend fun clear() = recorded.clear()
    }

    private class RecordingAlerts : AutomationAlerts {
        val results = mutableListOf<AutomationEventType>()
        val retryCommands = mutableListOf<DoorCommand>()

        override fun showCountdown(decision: DoorDecision, seconds: Int) = Unit

        override fun dismissCountdown() = Unit

        override fun showResult(event: AutomationEvent) {
            results += event.type
        }

        override fun showFailure(event: AutomationEvent, retryCommand: DoorCommand) {
            results += event.type
            retryCommands += retryCommand
        }
    }

    private class FakeDiagnostics : DiagnosticsLog {
        override val entries: Flow<List<DiagnosticEntry>> get() = MutableStateFlow(emptyList())

        override suspend fun record(message: String) = Unit

        override suspend fun clear() = Unit
    }
}
