package com.raf.garagedoor.automation

import com.raf.garagedoor.door.TransientRetry
import com.raf.garagedoor.myq.MyQApiException
import com.raf.garagedoor.myq.MyQAuthenticationException
import com.raf.garagedoor.myq.MyQNetworkException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransientRetryTest {

    @Test
    fun retriesNetworkAndServerErrorsUntilSuccess() = runTest {
        val failures = ArrayDeque(listOf(MyQNetworkException(IOException("timeout")), MyQApiException(503, "busy")))
        val retries = mutableListOf<Int>()
        val retry = TransientRetry(onRetry = { attempt, _ -> retries += attempt })

        val result = retry.run { failures.removeFirstOrNull()?.let { throw it } ?: "ok" }

        assertEquals("ok", result)
        assertEquals(listOf(1, 2), retries)
    }

    @Test
    fun givesUpAfterLastAttempt() = runTest {
        var attempts = 0

        val error = runCatching { TransientRetry().run<Unit> { attempts++; throw MyQNetworkException(IOException("down")) } }.exceptionOrNull()

        assertTrue(error is MyQNetworkException)
        assertEquals(TransientRetry.DEFAULT_DELAYS.size + 1, attempts)
    }

    @Test
    fun doesNotRetrySessionOrClientErrors() = runTest {
        var attempts = 0

        runCatching { TransientRetry().run<Unit> { attempts++; throw MyQAuthenticationException("expired") } }
        runCatching { TransientRetry().run<Unit> { attempts++; throw MyQApiException(404, "missing") } }

        assertEquals(2, attempts)
    }
}
