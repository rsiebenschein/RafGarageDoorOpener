package com.raf.garagedoor.myq

import com.raf.garagedoor.myq.api.MyQDeviceApi
import com.raf.garagedoor.myq.auth.AppCheckTokenMinter
import com.raf.garagedoor.myq.auth.AuthorizationUrlFactory
import com.raf.garagedoor.myq.auth.MyQSession
import com.raf.garagedoor.myq.auth.MyQTokenService
import com.raf.garagedoor.myq.auth.OAuthTokens
import com.raf.garagedoor.myq.auth.TokenStore
import com.raf.garagedoor.myq.config.MyQApiConfig
import com.raf.garagedoor.myq.http.MyQHttpExecutor
import com.raf.garagedoor.myq.http.MyQRequestHeaders
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InMemoryTokenStore(var tokens: OAuthTokens? = null, var pendingVerifier: String? = null) : TokenStore {
    override suspend fun loadTokens(): OAuthTokens? = tokens

    override suspend fun saveTokens(tokens: OAuthTokens) {
        this.tokens = tokens
    }

    override suspend fun savePendingVerifier(verifier: String) {
        pendingVerifier = verifier
    }

    override suspend fun takePendingVerifier(): String? = pendingVerifier.also { pendingVerifier = null }

    override suspend fun clear() {
        tokens = null
        pendingVerifier = null
    }
}

/** Wires the real myQ classes against a [MockWebServer] standing in for every myQ and Firebase host. */
class MyQTestHarness(val server: MockWebServer, val tokenStore: InMemoryTokenStore = InMemoryTokenStore()) {
    val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    private val baseUrl = server.url("/").toString().trimEnd('/')
    val config = MyQApiConfig(
        identityBaseUrl = baseUrl,
        accountsBaseUrl = baseUrl,
        devicesBaseUrl = baseUrl,
        garageDevicesBaseUrl = baseUrl,
        firebaseAppCheckBaseUrl = baseUrl,
    )
    private val http = MyQHttpExecutor(OkHttpClient())
    private val headers = MyQRequestHeaders(config)

    val tokenService = MyQTokenService(config, http, headers, AppCheckTokenMinter(config, http), clock)
    val session = MyQSession(config, tokenService, tokenStore, AuthorizationUrlFactory(config), clock)
    val deviceApi = MyQDeviceApi(config, http, headers, session)

    companion object {
        val NOW: Instant = Instant.parse("2026-10-10T12:00:00Z")

        fun json(body: String, status: Int = 200): MockResponse =
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

        fun validTokens(accessToken: String = "access-1") =
            OAuthTokens(accessToken, "refresh-1", NOW.epochSecond + 3600)
    }
}
