package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.door.DoorCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePhrasesTest {

    @Test
    fun `confident open phrase opens`() {
        assertEquals(DoorCommand.OPEN, VoicePhrases.commandIn(heard("open" to 1.0, "garage" to 0.97, "door" to 0.91)))
    }

    @Test
    fun `confident close phrase closes, with or without the`() {
        assertEquals(DoorCommand.CLOSE, VoicePhrases.commandIn(heard("close" to 0.9, "garage" to 1.0, "door" to 1.0)))
        assertEquals(DoorCommand.CLOSE, VoicePhrases.commandIn(heard("close" to 0.9, "the" to 0.9, "garage" to 1.0, "door" to 1.0)))
    }

    @Test
    fun `door is optional`() {
        assertEquals(DoorCommand.OPEN, VoicePhrases.commandIn(heard("open" to 0.9, "the" to 0.9, "garage" to 0.95)))
        assertEquals(DoorCommand.CLOSE, VoicePhrases.commandIn(heard("[unk]" to 0.5, "close" to 0.8, "garage" to 0.9)))
    }

    @Test
    fun `confidence covers the door word when it was said`() {
        val heard = heard("open" to 1.0, "the" to 1.0, "garage" to 1.0, "door" to 0.65)
        assertEquals(VoiceCommand(DoorCommand.OPEN, 0.65), VoicePhrases.phraseIn(heard))
    }

    @Test
    fun `phrase after unknown words still counts, scored on its own words`() {
        val heard = heard("[unk]" to 0.2, "open" to 0.8, "the" to 0.79, "garage" to 0.95, "door" to 1.0)
        assertEquals(VoiceCommand(DoorCommand.OPEN, 0.79), VoicePhrases.phraseIn(heard))
        assertEquals(DoorCommand.OPEN, VoicePhrases.commandIn(heard))
    }

    @Test
    fun `phrase with a doubtful word is not a command`() {
        val heard = heard("close" to 1.0, "garage" to 0.4, "door" to 0.95)
        assertNull(VoicePhrases.commandIn(heard))
        assertTrue(VoicePhrases.isNearMiss(heard))
    }

    /** These are what the phone heard for "Is the door open" and "Can you open the window please". */
    @Test
    fun `similar sentences without a phrase are not commands`() {
        assertNull(VoicePhrases.commandIn(heard("[unk]" to 1.0, "the" to 0.8, "door" to 0.8, "open" to 0.8)))
        assertNull(VoicePhrases.commandIn(heard("[unk]" to 1.0, "open" to 1.0, "the" to 1.0, "[unk]" to 1.0)))
        assertNull(VoicePhrases.commandIn(heard("[unk]" to 1.0, "the" to 1.0, "garage" to 1.0, "door" to 1.0, "close" to 1.0)))
    }

    @Test
    fun `other speech is neither a command nor worth logging`() {
        val heard = heard("[unk]" to 1.0)
        assertNull(VoicePhrases.commandIn(heard))
        assertFalse(VoicePhrases.isNearMiss(heard))
    }

    @Test
    fun `silence and malformed results are ignored`() {
        assertNull(VoicePhrases.parse("""{"text":""}"""))
        assertNull(VoicePhrases.parse("not json"))
    }

    @Test
    fun `grammar lists the phrases and the unknown word`() {
        val expected = "[\"open the garage door\", \"open garage door\", \"open the garage\", \"open garage\", " +
            "\"close the garage door\", \"close garage door\", \"close the garage\", \"close garage\", \"[unk]\"]"
        assertEquals(expected, VoicePhrases.GRAMMAR)
    }

    private fun heard(vararg words: Pair<String, Double>): HeardPhrase {
        val json = words.joinToString(",") { (word, conf) -> """{"conf":$conf,"word":"$word"}""" }
        val text = words.joinToString(" ") { it.first }
        return checkNotNull(VoicePhrases.parse("""{"result":[$json],"text":"$text"}"""))
    }
}
