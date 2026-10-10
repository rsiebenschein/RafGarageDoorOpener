package com.raf.garagedoor.myq.api

import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

object MyQDoorMapper {
    private const val GARAGE_DOOR_FAMILY = "garagedoor"

    fun toGarageDoors(accountId: String, devices: List<DeviceDto>): List<GarageDoor> = devices
        .filter { it.deviceFamily == GARAGE_DOOR_FAMILY }
        .map { toGarageDoor(accountId, it) }

    fun toDoorState(rawState: String?): DoorState = when (rawState) {
        "open" -> DoorState.OPEN
        "closed" -> DoorState.CLOSED
        "opening" -> DoorState.OPENING
        "closing" -> DoorState.CLOSING
        "stopped" -> DoorState.STOPPED
        "moving" -> DoorState.MOVING
        else -> DoorState.UNKNOWN
    }

    private fun toGarageDoor(accountId: String, device: DeviceDto) = GarageDoor(
        id = DoorId(accountId, device.serialNumber),
        name = device.name ?: device.serialNumber,
        model = device.deviceModel,
        state = toDoorState(device.state?.doorState),
        isOnline = device.state?.online == true,
        lastChanged = device.state?.lastUpdate?.let(::parseTimestamp),
    )

    /** myQ timestamps are ISO-8601, sometimes without an offset; those are UTC. */
    private fun parseTimestamp(raw: String): Instant? = try {
        OffsetDateTime.parse(raw).toInstant()
    } catch (missingOffset: DateTimeParseException) {
        parseUtcLocalTimestamp(raw)
    }

    private fun parseUtcLocalTimestamp(raw: String): Instant? = try {
        LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC)
    } catch (unparseable: DateTimeParseException) {
        null
    }
}
