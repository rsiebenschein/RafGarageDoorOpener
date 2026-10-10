package com.raf.garagedoor.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Lets the notification's Cancel action abort a pending automatic command in the same process. */
class CommandCountdown {

    private val cancelRequested = MutableStateFlow(false)

    /** Waits [seconds]; returns `false` if [cancel] was called meanwhile. Zero seconds proceeds at once. */
    suspend fun completesWithoutCancel(seconds: Int): Boolean {
        if (seconds <= 0) return true
        cancelRequested.value = false
        val cancelled = withTimeoutOrNull(seconds * MILLIS_PER_SECOND) { cancelRequested.first { it } }
        return cancelled == null
    }

    fun cancel() {
        cancelRequested.value = true
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
