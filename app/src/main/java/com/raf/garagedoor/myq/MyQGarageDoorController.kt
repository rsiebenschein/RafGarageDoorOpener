package com.raf.garagedoor.myq

import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.myq.api.MyQDeviceApi
import com.raf.garagedoor.myq.api.MyQDoorMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MyQGarageDoorController(private val api: MyQDeviceApi) : GarageDoorController {

    /** Account ids rarely change, so they are fetched once to halve the requests made per poll. */
    @Volatile
    private var cachedAccountIds: List<String>? = null

    override suspend fun listDoors(): List<GarageDoor> = coroutineScope {
        accountIds()
            .map { accountId -> async { MyQDoorMapper.toGarageDoors(accountId, api.fetchDevices(accountId)) } }
            .awaitAll()
            .flatten()
    }

    override suspend fun execute(door: DoorId, command: DoorCommand) {
        try {
            api.sendDoorCommand(door.accountId, door.serialNumber, command)
        } catch (error: MyQApiException) {
            if (error.statusCode == HTTP_FORBIDDEN) throw MyQDoorUnavailableException(door.serialNumber)
            throw error
        }
    }

    private suspend fun accountIds(): List<String> =
        cachedAccountIds ?: api.fetchAccounts().map { it.id }.also { cachedAccountIds = it }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
