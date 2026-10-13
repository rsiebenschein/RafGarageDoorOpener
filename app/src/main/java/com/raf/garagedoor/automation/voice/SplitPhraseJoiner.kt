package com.raf.garagedoor.automation.voice

import java.time.Duration
import java.time.Instant

/**
 * Vosk ends an utterance at a short pause, so "Open the ... garage" can arrive as two results,
 * "open the" and "garage", neither of them a command (field tests 5 and 6 logged "garage" and
 * "garage door" on their own). Each result is therefore matched joined to the one before it, if
 * that one came within [MAX_GAP].
 */
class SplitPhraseJoiner {
    private var previous: Pair<HeardPhrase, Instant>? = null

    /** This result, joined to the previous one when that came moments ago. */
    fun withRecent(heard: HeardPhrase, at: Instant): HeardPhrase {
        val recent = previous?.takeIf { (_, previousAt) -> Duration.between(previousAt, at) <= MAX_GAP }?.first
        previous = heard to at
        return recent?.let { VoicePhrases.joined(it, heard) } ?: heard
    }

    /** After a command, so its words cannot count toward the next one. */
    fun forget() {
        previous = null
    }

    companion object {
        val MAX_GAP: Duration = Duration.ofSeconds(4)
    }
}
