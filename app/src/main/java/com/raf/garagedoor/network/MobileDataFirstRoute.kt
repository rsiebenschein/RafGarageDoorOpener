package com.raf.garagedoor.network

import android.net.Network
import com.raf.garagedoor.myq.http.MyQHttpRoute
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.time.Duration

/**
 * Sends myQ calls over mobile data first and falls back to the default network (usually Wi-Fi);
 * routine status checks ([RoutineStatusCheck]) go the other way round.
 * Field tests saw the phone stay connected to weak home Wi-Fi with no internet at the edge of
 * range, so a hands-free close failed until Wi-Fi was turned off. Sending again over Wi-Fi after a
 * mobile failure is safe: myQ's open and close are "go to this state", not toggles.
 *
 * [record] gets a Diagnostics line whenever the route changes or mobile data fails, not per call.
 */
class MobileDataFirstRoute(
    private val defaultClient: OkHttpClient,
    private val mobileData: MobileDataNetwork,
    private val isMobileDataAllowed: suspend () -> Boolean,
    private val record: suspend (String) -> Unit,
) : MyQHttpRoute {

    @Volatile
    private var lastRoute: String? = null

    override suspend fun <T> call(send: (OkHttpClient) -> T): T {
        if (!isMobileDataAllowed()) return sendOverDefault("mobile data switched off in settings", send)
        if (RoutineStatusCheck.isActiveIn(currentCoroutineContext())) return sendRoutineCheck(send)
        return mobileData.use { access ->
            when (access) {
                is MobileDataAccess.Ready -> sendOverMobileFirst(access.network, send)
                is MobileDataAccess.Unusable -> sendOverDefault(access.reason, send)
            }
        }
    }

    /** Called while a door command is likely soon (departure check, last stretch home). */
    suspend fun prepareForDoorCommand() {
        if (isMobileDataAllowed()) mobileData.keepWarmFor(WARM_FOR_COMMAND_MILLIS)
    }

    private suspend fun <T> sendOverMobileFirst(network: Network, send: (OkHttpClient) -> T): T = try {
        send(clientFor(network)).also { noteRoute("myQ requests now go over mobile data") }
    } catch (mobileFailed: IOException) {
        mobileData.skipAfterFailedCall()
        record("myQ over mobile data failed, trying Wi-Fi: ${mobileFailed.message}")
        sendOverDefault("mobile data call failed", send)
    }

    /** Wi-Fi first, mobile data only if that fails; no route note, so checks do not flood Diagnostics. */
    private suspend fun <T> sendRoutineCheck(send: (OkHttpClient) -> T): T = try {
        send(defaultClient)
    } catch (defaultFailed: IOException) {
        mobileData.use { access -> if (access is MobileDataAccess.Ready) send(clientFor(access.network)) else throw defaultFailed }
    }

    private suspend fun <T> sendOverDefault(reason: String, send: (OkHttpClient) -> T): T {
        noteRoute("myQ requests now go over Wi-Fi (or the phone's default network): $reason")
        return send(defaultClient)
    }

    private suspend fun noteRoute(route: String) {
        if (route == lastRoute) return
        lastRoute = route
        record(route)
    }

    /** OkHttp keys pooled connections by socket factory and DNS, so these never mix with Wi-Fi ones. */
    private fun clientFor(network: Network): OkHttpClient = defaultClient.newBuilder()
        .socketFactory(network.socketFactory)
        .dns(NetworkDns(network))
        .connectTimeout(MOBILE_CONNECT_TIMEOUT)
        .callTimeout(MOBILE_CALL_TIMEOUT)
        .build()

    /** Looks names up through the mobile network too: Wi-Fi with no internet fails DNS first. */
    private class NetworkDns(private val network: Network) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = network.getAllByName(hostname).toList()
    }

    private companion object {
        /** Short, so a weak signal in the garage hands over to Wi-Fi quickly. */
        val MOBILE_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val MOBILE_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)

        /** Renewed by every precise location fix, so it lapses a minute after GPS tracking stops. */
        const val WARM_FOR_COMMAND_MILLIS = 60_000L
    }
}
