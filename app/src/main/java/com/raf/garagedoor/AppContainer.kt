package com.raf.garagedoor

import android.content.Context
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.geofence.DataStoreGeofenceSettingsRepository
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.location.CurrentLocationProvider
import com.raf.garagedoor.location.FusedCurrentLocationProvider
import com.raf.garagedoor.myq.MyQGarageDoorController
import com.raf.garagedoor.myq.api.MyQDeviceApi
import com.raf.garagedoor.myq.auth.AppCheckTokenMinter
import com.raf.garagedoor.myq.auth.AuthorizationUrlFactory
import com.raf.garagedoor.myq.auth.MyQSession
import com.raf.garagedoor.myq.auth.MyQTokenService
import com.raf.garagedoor.myq.config.MyQApiConfig
import com.raf.garagedoor.myq.http.MyQHttpExecutor
import com.raf.garagedoor.myq.http.MyQRequestHeaders
import com.raf.garagedoor.storage.EncryptedTokenStore
import com.raf.garagedoor.storage.KeystoreCipher
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Duration

/** Manual dependency graph; small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()

    private val config = MyQApiConfig()
    private val headers = MyQRequestHeaders(config)
    private val http = MyQHttpExecutor(
        OkHttpClient.Builder().callTimeout(Duration.ofSeconds(HTTP_CALL_TIMEOUT_SECONDS)).build(),
    )

    val myQSession = MyQSession(
        config = config,
        tokenService = MyQTokenService(config, http, headers, AppCheckTokenMinter(config, http), clock),
        tokenStore = EncryptedTokenStore(context, KeystoreCipher(TOKEN_KEY_ALIAS)),
        authorizationUrlFactory = AuthorizationUrlFactory(config),
        clock = clock,
    )

    val garageDoorController: GarageDoorController =
        MyQGarageDoorController(MyQDeviceApi(config, http, headers, myQSession))

    val geofenceSettings: GeofenceSettingsRepository = DataStoreGeofenceSettingsRepository(context)

    val currentLocationProvider: CurrentLocationProvider = FusedCurrentLocationProvider(context)

    private companion object {
        const val HTTP_CALL_TIMEOUT_SECONDS = 30L
        const val TOKEN_KEY_ALIAS = "myq_session_key"
    }
}
