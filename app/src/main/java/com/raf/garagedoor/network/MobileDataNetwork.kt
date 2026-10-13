package com.raf.garagedoor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Whether mobile data can carry the next myQ call, and if not, why (shown in Diagnostics). */
sealed interface MobileDataAccess {
    data class Ready(val network: Network) : MobileDataAccess
    data class Unusable(val reason: String) : MobileDataAccess
}

/**
 * Mobile data, held open for myQ calls even while Wi-Fi is connected. Never while roaming: a
 * roaming phone skips it at once, and a network that turns out to be roaming is not used (Android
 * does not let an app ask for "not roaming" up front).
 *
 * Bringing mobile data up while on Wi-Fi can take several seconds. Field test 6 saw the status read
 * before a close get it, the release tear it down, and the close itself then wait over 3 s for it
 * and fall back to failing Wi-Fi. So the request is held: for [LINGER_MILLIS] after each call (the
 * read, the command and its retries share one connection), for as long as [keepWarmFor] asks
 * (departure checks, the last stretch home), and while it is still coming up, so a retry uses it
 * the moment it is ready.
 */
class MobileDataNetwork(context: Context, private val scope: CoroutineScope) {

    private val contentResolver = context.contentResolver
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val lock = Mutex()
    private var held: HeldRequest? = null
    private var releaseAtElapsedMillis = 0L
    private var releaseJob: Job? = null

    @Volatile
    private var skipUntilElapsedMillis = 0L

    /** Runs [block] with mobile data, or with the reason it cannot be used right now. */
    suspend fun <T> use(block: suspend (MobileDataAccess) -> T): T {
        unusableReason()?.let { return block(MobileDataAccess.Unusable(it)) }
        try {
            return block(acquire())
        } finally {
            releaseAfter(LINGER_MILLIS)
        }
    }

    /** Brings mobile data up ahead of a likely door command and keeps it for [millis]. */
    suspend fun keepWarmFor(millis: Long) {
        if (unusableReason() != null) return
        lock.withLock { heldOrNewRequest() }
        releaseAfter(millis)
    }

    /** After a failed call over mobile data, the next attempt tries the default network first. */
    fun skipAfterFailedCall() = skipFor(SKIP_AFTER_FAILED_CALL_MILLIS)

    private fun skipFor(millis: Long) {
        skipUntilElapsedMillis = SystemClock.elapsedRealtime() + millis
    }

    private fun unusableReason(): String? = when {
        SystemClock.elapsedRealtime() < skipUntilElapsedMillis -> "mobile data failed or was unavailable moments ago"
        isAirplaneModeOn() -> "airplane mode"
        telephony.isNetworkRoaming -> "roaming"
        !isMobileDataSwitchedOn() -> "mobile data is switched off"
        else -> null
    }

    /** Field test 6: overnight airplane mode (Wi-Fi on) made each voice command wait for mobile data first. */
    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun isMobileDataSwitchedOn(): Boolean = try {
        telephony.isDataEnabled
    } catch (denied: SecurityException) {
        true // Unknown: let the request itself find out.
    }

    private suspend fun acquire(): MobileDataAccess {
        val requested = lock.withLock {
            releaseJob?.cancel()
            heldOrNewRequest()
        }
        return when (requested) {
            is Requested.Held -> awaitUsableNetwork(requested.request)
            is Requested.Refused -> MobileDataAccess.Unusable(requested.reason)
        }
    }

    /** Call with [lock] held. Reuses the held request unless Android has since dropped its network. */
    private fun heldOrNewRequest(): Requested {
        held?.takeUnless { it.isGone }?.let { return Requested.Held(it) }
        unregisterHeld()
        val request = HeldRequest()
        return try {
            connectivity.requestNetwork(MOBILE_DATA, request)
            held = request
            Requested.Held(request)
        } catch (invalid: IllegalArgumentException) {
            refused(invalid)
        } catch (denied: SecurityException) {
            refused(denied)
        }
    }

    /** A refusal must never crash a door command; mobile data is then left alone for a minute. */
    private fun refused(refusal: RuntimeException): Requested {
        skipFor(SKIP_AFTER_REFUSAL_MILLIS)
        return Requested.Refused("Android refused the mobile data request: ${refusal.message}")
    }

    /** Waits a few seconds; a request that is still coming up stays held for the next attempt. */
    private suspend fun awaitUsableNetwork(request: HeldRequest): MobileDataAccess {
        val network = withTimeoutOrNull(ACQUIRE_WAIT_MILLIS) { request.network.await() }
            ?: return MobileDataAccess.Unusable("no mobile network came up within ${ACQUIRE_WAIT_MILLIS / 1000} s")
        if (!isRoaming(network)) return MobileDataAccess.Ready(network)
        skipFor(SKIP_AFTER_REFUSAL_MILLIS)
        lock.withLock { unregisterHeld() }
        return MobileDataAccess.Unusable("roaming")
    }

    private fun isRoaming(network: Network): Boolean =
        connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) != true

    /** Keeps the request until [millis] from now, or longer if something already asked for longer. */
    private suspend fun releaseAfter(millis: Long) = lock.withLock {
        val now = SystemClock.elapsedRealtime()
        releaseAtElapsedMillis = maxOf(releaseAtElapsedMillis, now + millis)
        val wait = releaseAtElapsedMillis - now
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(wait)
            lock.withLock { unregisterHeld() }
        }
    }

    /** Call with [lock] held. */
    private fun unregisterHeld() {
        val request = held ?: return
        held = null
        try {
            connectivity.unregisterNetworkCallback(request)
        } catch (alreadyGone: IllegalArgumentException) {
            // Android had already dropped the request.
        }
    }

    private sealed interface Requested {
        data class Held(val request: HeldRequest) : Requested
        data class Refused(val reason: String) : Requested
    }

    private class HeldRequest : ConnectivityManager.NetworkCallback() {
        val network = CompletableDeferred<Network>()

        @Volatile
        var isGone = false
            private set

        override fun onAvailable(network: Network) {
            this.network.complete(network)
        }

        override fun onLost(network: Network) {
            isGone = true
        }

        override fun onUnavailable() {
            isGone = true
        }
    }

    private companion object {
        const val ACQUIRE_WAIT_MILLIS = 5_000L
        const val LINGER_MILLIS = 30_000L
        const val SKIP_AFTER_REFUSAL_MILLIS = 60_000L
        const val SKIP_AFTER_FAILED_CALL_MILLIS = 10_000L

        val MOBILE_DATA: NetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
    }
}
