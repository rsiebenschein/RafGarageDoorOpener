package com.raf.garagedoor.network

import android.net.Network
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the real phone (mobile data on, not roaming): once warmed, mobile data is ready at once, and
 * a second call within the linger time reuses the same connection instead of bringing it up again.
 */
@RunWith(AndroidJUnit4::class)
class MobileDataNetworkOnDeviceTest {

    @Test
    fun warmedMobileDataIsReadyAtOnceAndReusedByTheNextCall() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mobileData = MobileDataNetwork(InstrumentationRegistry.getInstrumentation().targetContext, scope)
        mobileData.keepWarmFor(WARM_MILLIS)
        val first = mobileData.use { it }
        val secondStarted = SystemClock.elapsedRealtime()
        val second = mobileData.use { it }
        val secondWaitMillis = SystemClock.elapsedRealtime() - secondStarted
        Log.i(LOG_TAG, "first=$first second=$second secondWait=${secondWaitMillis}ms")
        scope.cancel()
        assertTrue("first: $first", first is MobileDataAccess.Ready)
        assertEquals(networkOf(first), networkOf(second))
        assertTrue("second call waited ${secondWaitMillis}ms", secondWaitMillis < MAX_REUSE_WAIT_MILLIS)
    }

    private fun networkOf(access: MobileDataAccess): Network? = (access as? MobileDataAccess.Ready)?.network

    private companion object {
        const val WARM_MILLIS = 15_000L
        const val MAX_REUSE_WAIT_MILLIS = 200L
        const val LOG_TAG = "MobileDataTest"
    }
}
