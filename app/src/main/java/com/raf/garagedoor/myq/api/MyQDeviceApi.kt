package com.raf.garagedoor.myq.api

import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.myq.MyQAuthenticationException
import com.raf.garagedoor.myq.auth.MyQSession
import com.raf.garagedoor.myq.config.MyQApiConfig
import com.raf.garagedoor.myq.http.MyQHttpExecutor
import com.raf.garagedoor.myq.http.MyQJson
import com.raf.garagedoor.myq.http.MyQRequestHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MyQDeviceApi(
    private val config: MyQApiConfig,
    private val http: MyQHttpExecutor,
    private val headers: MyQRequestHeaders,
    private val session: MyQSession,
) {
    suspend fun fetchAccounts(): List<AccountDto> {
        val url = buildUrl(config.accountsBaseUrl, "api", "v6.0", "accounts")
        return MyQJson.decode<AccountsResponse>(authorizedGet(url)).accounts
    }

    suspend fun fetchDevices(accountId: String): List<DeviceDto> {
        val url = buildUrl(config.devicesBaseUrl, "api", "v6.2", "Accounts", accountId, "Devices")
        return MyQJson.decode<DevicesResponse>(authorizedGet(url)).items
    }

    suspend fun sendDoorCommand(accountId: String, serialNumber: String, command: DoorCommand) {
        val path = command.name.lowercase()
        val url = buildUrl(config.garageDevicesBaseUrl, "api", "v6.0", "accounts", accountId, "door_openers", serialNumber, path)
        // A 403 on a command means the door is offline, not that the session is invalid.
        authorizedCall(COMMAND_SESSION_FAILURE_STATUSES) { token ->
            Request.Builder().url(url).headers(headers.authorizedApi(token)).put(EMPTY_BODY).build()
        }
    }

    private suspend fun authorizedGet(url: HttpUrl): String =
        authorizedCall(MyQHttpExecutor.DEFAULT_SESSION_FAILURE_STATUSES) { token ->
            Request.Builder().url(url).headers(headers.authorizedApi(token)).get().build()
        }

    /** Retries once with a force-refreshed token when myQ rejects a token that looked valid. */
    private suspend fun authorizedCall(sessionFailureStatuses: Set<Int>, buildRequest: (String) -> Request): String =
        try {
            http.execute(buildRequest(session.validAccessToken()), sessionFailureStatuses)
        } catch (rejected: MyQAuthenticationException) {
            http.execute(buildRequest(session.forceRefresh()), sessionFailureStatuses)
        }

    private fun buildUrl(baseUrl: String, vararg pathSegments: String): HttpUrl =
        baseUrl.toHttpUrl().newBuilder()
            .apply { pathSegments.forEach(::addPathSegment) }
            .build()

    private companion object {
        val COMMAND_SESSION_FAILURE_STATUSES = setOf(401)
        val EMPTY_BODY = ByteArray(0).toRequestBody()
    }
}
