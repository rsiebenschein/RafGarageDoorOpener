package com.raf.garagedoor.door

import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Retries a door operation after transient failures (network drops, myQ server errors), waiting
 * [delays] between attempts. Session and rate-limit failures are rethrown immediately.
 */
class TransientRetry(
    private val delays: List<Duration> = DEFAULT_DELAYS,
    private val onRetry: suspend (attempt: Int, error: GarageDoorControlException) -> Unit = { _, _ -> },
) {
    suspend fun <T> run(operation: suspend () -> T): T {
        delays.forEachIndexed { index, wait ->
            try {
                return operation()
            } catch (error: GarageDoorControlException) {
                if (!error.isTransient) throw error
                onRetry(index + 1, error)
                delay(wait.toMillis())
            }
        }
        return operation()
    }

    companion object {
        /** Three attempts over roughly 10 seconds of waiting, enough to ride out a Wi-Fi to cellular handoff. */
        val DEFAULT_DELAYS: List<Duration> = listOf(Duration.ofSeconds(3), Duration.ofSeconds(7))

        /** Five attempts over about a minute of waiting, for commands nobody is watching on screen. */
        val BACKGROUND_DELAYS: List<Duration> = listOf(3L, 7L, 15L, 30L).map(Duration::ofSeconds)
    }
}
