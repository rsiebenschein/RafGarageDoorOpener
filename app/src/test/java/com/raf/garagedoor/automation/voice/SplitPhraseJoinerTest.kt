package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.door.DoorCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SplitPhraseJoinerTest {

    private val start = Instant.parse("2026-10-13T08:39:50Z")
    private val joiner = SplitPhraseJoiner()

    @Test
    fun `a phrase split by a pause is a command once joined`() {
        joiner.withRecent(heard("open", "the"), start)
        val joined = joiner.withRecent(heard("garage"), start.plusSeconds(2))

        assertEquals("open the garage", joined.text)
        assertEquals(DoorCommand.OPEN, VoicePhrases.commandIn(joined))
    }

    @Test
    fun `pieces too far apart are not joined`() {
        joiner.withRecent(heard("close"), start)
        val later = joiner.withRecent(heard("garage"), start.plus(SplitPhraseJoiner.MAX_GAP).plusSeconds(1))

        assertNull(VoicePhrases.commandIn(later))
    }

    @Test
    fun `nothing carries over after a command`() {
        joiner.withRecent(heard("open", "the", "garage"), start)
        joiner.forget()
        val next = joiner.withRecent(heard("door"), start.plusSeconds(1))

        assertEquals("door", next.text)
    }

    private fun heard(vararg words: String) = HeardPhrase(words.joinToString(" "), words.map { HeardWord(it, 1.0) })
}
