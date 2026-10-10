package com.raf.garagedoor.myq.api

import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.myq.http.MyQJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MyQDoorMapperTest {

    private val devicesJson = """
        {"count": 2, "items": [
          {"serial_number": "CG0846B28913", "name": "Garage Door", "device_family": "garagedoor",
           "device_model": "virtualgaragedooropener", "unexpected_field": 1,
           "state": {"door_state": "closed", "online": true, "last_update": "2026-10-10T14:03:11.123Z"}},
          {"serial_number": "GW123", "name": "Hub", "device_family": "gateway", "state": {"online": true}}
        ]}
    """.trimIndent()

    @Test
    fun toGarageDoors_keepsOnlyGarageDoorsAndMapsFields() {
        val devices = MyQJson.decode<DevicesResponse>(devicesJson).items

        val doors = MyQDoorMapper.toGarageDoors("account-1", devices)

        assertEquals(1, doors.size)
        val door = doors.single()
        assertEquals(DoorId("account-1", "CG0846B28913"), door.id)
        assertEquals(DoorState.CLOSED, door.state)
        assertEquals(true, door.isOnline)
        assertEquals(Instant.parse("2026-10-10T14:03:11.123Z"), door.lastChanged)
    }

    @Test
    fun toGarageDoors_treatsOffsetlessTimestampsAsUtcAndIgnoresGarbage() {
        val devices = listOf(
            garageDevice("A", lastUpdate = "2026-10-10T14:03:11"),
            garageDevice("B", lastUpdate = "not a date"),
        )

        val doors = MyQDoorMapper.toGarageDoors("account-1", devices)

        assertEquals(Instant.parse("2026-10-10T14:03:11Z"), doors[0].lastChanged)
        assertNull(doors[1].lastChanged)
    }

    @Test
    fun toDoorState_mapsEveryKnownMyQStateAndFallsBackToUnknown() {
        val expected = mapOf(
            "open" to DoorState.OPEN, "closed" to DoorState.CLOSED, "opening" to DoorState.OPENING,
            "closing" to DoorState.CLOSING, "stopped" to DoorState.STOPPED, "moving" to DoorState.MOVING,
            "autoreverse" to DoorState.UNKNOWN, null to DoorState.UNKNOWN,
        )

        expected.forEach { (raw, state) -> assertEquals(state, MyQDoorMapper.toDoorState(raw)) }
    }

    private fun garageDevice(serial: String, lastUpdate: String) = DeviceDto(
        serialNumber = serial,
        deviceFamily = "garagedoor",
        state = DeviceStateDto(doorState = "open", online = true, lastUpdate = lastUpdate),
    )
}
