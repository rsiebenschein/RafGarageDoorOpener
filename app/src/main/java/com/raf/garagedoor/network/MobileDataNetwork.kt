package com.raf.garagedoor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import android.telephony.TelephonyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Whether mobile data can carry the next myQ call, and if not, why (shown in Diagnostics). */
sealed interface MobileDataAccess {
    data class Ready(val network: Network) : MobileDataAccess
    data class Unusable(val reason: String) : MobileDataAccess
}

/**
 * Mobile data, held open for one myQ call even while Wi-Fi is connected. Never while roaming: a
 * roaming phone skips it at once, and a network that turns out to be roaming is not used (Android
 * does not let an app ask for "not roaming" up front). When mobile data does not come up (no
 * signal, data switched off) it is skipped for a minute, and after a failed call for 20 s, so the
 * screen's polling does not wait for it every time while a command's later retries try it again.
 */
class MobileDataNetwork(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    @Volatile
    private var skipUntilElapsedMillis = 0L

    /** Runs [block] with mobile data held open, or with the reason it cannot be used right now. */
    suspend fun <T> use(block: suspend (MobileDataAccess) -> T): T {
        unusableReason()?.let { return block(MobileDataAccess.Unusable(it)) }
        val callback = AwaitedNetworkCallback()
        requestOrExplainRefusal(callback)?.let { refusal -> return block(MobileDataAccess.Unusable(refusal)) }
        try {
            return block(awaitHomeNetwork(callback))
        } finally {
            connectivity.unregisterNetworkCallback(callback)
        }
    }

    /** After a failed call over mobile data, go straight to the default network for a short while. */
    fun skipAfterFailedCall() = skipFor(SKIP_AFTER_FAILED_CALL_MILLIS)

    private fun skipFor(millis: Long) {
        skipUntilElapsedMillis = SystemClock.elapsedRealtime() + millis
    }

    private fun unusableReason(): String? = when {
        SystemClock.elapsedRealtime() < skipUntilElapsedMillis -> "mobile data failed or was unavailable moments ago"
        telephony.isNetworkRoaming -> "roaming"
        !isMobileDataSwitchedOn() -> "mobile data is switched off"
        else -> null
    }

    private fun isMobileDataSwitchedOn(): Boolean = try {
        telephony.isDataEnabled
    } catch (denied: SecurityException) {
        true // Unknown: let the request itself find out.
    }

    /** Returns null once requested, or why Android refused; a refusal must never crash a door command. */
    private fun requestOrExplainRefusal(callback: AwaitedNetworkCallback): String? {
        val refusal = try {
            connectivity.requestNetwork(MOBILE_DATA, callback, ACQUIRE_TIMEOUT_MILLIS.toInt())
            return null
        } catch (invalid: IllegalArgumentException) {
            invalid
        } catch (denied: SecurityException) {
            denied
        }
        skipFor(SKIP_AFTER_UNAVAILABLE_MILLIS)
        return "Android refused the mobile data request: ${refusal.message}"
    }

    private suspend fun awaitHomeNetwork(callback: AwaitedNetworkCallback): MobileDataAccess {
        val network = withTimeoutOrNull(ACQUIRE_TIMEOUT_MILLIS + TIMEOUT_GRACE_MILLIS) { callback.network.await() }
        val problem = when {
            network == null -> "no mobile network came up within ${ACQUIRE_TIMEOUT_MILLIS / 1000} s"
            isRoaming(network) -> "roaming"
            else -> return MobileDataAccess.Ready(network)
        }
        skipFor(SKIP_AFTER_UNAVAILABLE_MILLIS)
        return MobileDataAccess.Unusable(problem)
    }

    private fun isRoaming(network: Network): Boolean =
        connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) != true

    private class AwaitedNetworkCallback : ConnectivityManager.NetworkCallback() {
        val network = CompletableDeferred<Network?>()

        override fun onAvailable(network: Network) {
            this.network.complete(network)
        }

        override fun onUnavailable() {
            network.complete(null)
        }
    }

    private companion object {
        const val ACQUIRE_TIMEOUT_MILLIS = 3_000L
        const val TIMEOUT_GRACE_MILLIS = 500L
        const val SKIP_AFTER_UNAVAILABLE_MILLIS = 60_000L
        const val SKIP_AFTER_FAILED_CALL_MILLIS = 20_000L

        val MOBILE_DATA: NetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
    }
}
