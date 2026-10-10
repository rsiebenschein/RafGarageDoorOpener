package com.raf.garagedoor.myq

import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.myq.MyQTestHarness.Companion.json
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyQGarageDoorControllerTest {

    private val server = MockWebServer()
    private val harness = MyQTestHarness(server, InMemoryTokenStore(tokens = MyQTestHarness.validTokens()))
    private val controller = MyQGarageDoorController(harness.deviceApi)

    private val accountsJson = """{"accounts": [{"id": "acct-1", "name": "Home"}]}"""
    private val devicesJson = """{"items": [{"serial_number": "CG1", "name": "Garage Door",
        "device_family": "garagedoor", "state": {"door_state": "open", "online": true}}]}"""

    @After
    fun shutDownServer() = server.shutdown()

    @Test
    fun listDoors_sendsMyQHeadersAndCachesAccountsAcrossPolls() = runTest {
        server.enqueue(json(accountsJson))
        server.enqueue(json(devicesJson))
        server.enqueue(json(devicesJson))

        controller.listDoors()
        val doors = controller.listDoors()

        assertEquals(DoorState.OPEN, doors.single().state)
        assertEquals(3, server.requestCount)
        val accountsRequest = server.takeRequest()
        assertEquals("/api/v6.0/accounts", accountsRequest.path)
        assertEquals("Bearer access-1", accountsRequest.getHeader("Authorization"))
        assertEquals(harness.config.appVersion, accountsRequest.getHeader("App-Version"))
        assertEquals("/api/v6.2/Accounts/acct-1/Devices", server.takeRequest().path)
    }

    @Test
    fun listDoors_retriesOnceWithRefreshedTokenWhenApiRejectsToken() = runTest {
        server.enqueue(json("{}", status = 401))
        server.enqueue(json("""{"access_token": "access-2", "expires_in": 3600}"""))
        server.enqueue(json(accountsJson))
        server.enqueue(json(devicesJson))

        val doors = controller.listDoors()

        assertEquals(1, doors.size)
        server.takeRequest()
        assertEquals("/connect/token", server.takeRequest().path)
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun execute_putsCommandToDoorOpenerEndpoint() = runTest {
        server.enqueue(json("{}", status = 202))

        controller.execute(DoorId("acct-1", "CG1"), DoorCommand.CLOSE)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v6.0/accounts/acct-1/door_openers/CG1/close", request.path)
    }

    @Test
    fun execute_mapsForbiddenToDoorUnavailableWithoutSigningOut() = runTest {
        server.enqueue(json("{}", status = 403))

        val error = runCatching { controller.execute(DoorId("acct-1", "CG1"), DoorCommand.OPEN) }.exceptionOrNull()

        assertTrue(error is MyQDoorUnavailableException)
        assertEquals("access-1", harness.tokenStore.tokens?.accessToken)
    }

    @Test
    fun execute_mapsTooManyRequestsToRateLimited() = runTest {
        server.enqueue(json("{}", status = 429))

        val error = runCatching { controller.execute(DoorId("acct-1", "CG1"), DoorCommand.OPEN) }.exceptionOrNull()

        assertTrue(error is MyQRateLimitedException)
    }
}
