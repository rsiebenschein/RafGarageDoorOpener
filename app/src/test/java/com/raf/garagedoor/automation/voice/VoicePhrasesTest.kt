package com.raf.garagedoor.automation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePhrasesTest {

    @Test
    fun `confident open phrase is a command`() {
        assertTrue(VoicePhrases.isOpenCommand(heard("open" to 1.0, "garage" to 0.97, "door" to 0.91)))
    }

    @Test
    fun `open phrase after unknown words is still a command, scored on its own words`() {
        val heard = heard("[unk]" to 0.2, "open" to 0.8, "the" to 0.79, "garage" to 0.95, "door" to 1.0)
        assertEquals(0.79, VoicePhrases.openPhraseConfidence(heard)!!, 0.0)
        assertTrue(VoicePhrases.isOpenCommand(heard))
    }

    @Test
    fun `open phrase with a doubtful word is not a command`() {
        val heard = heard("open" to 1.0, "garage" to 0.4, "door" to 0.95)
        assertFalse(VoicePhrases.isOpenCommand(heard))
        assertTrue(VoicePhrases.isNearMiss(heard))
    }

    /** These are what the phone heard for "Close the garage door", "Is the door open" and "Can you open the window please". */
    @Test
    fun `similar sentences without the phrase are not commands`() {
        assertFalse(VoicePhrases.isOpenCommand(heard("[unk]" to 1.0, "the" to 1.0, "garage" to 1.0, "door" to 1.0)))
        assertFalse(VoicePhrases.isOpenCommand(heard("[unk]" to 1.0, "the" to 0.8, "door" to 0.8, "open" to 0.8)))
        assertFalse(VoicePhrases.isOpenCommand(heard("[unk]" to 1.0, "open" to 1.0, "the" to 1.0, "[unk]" to 1.0)))
    }

    @Test
    fun `other speech is neither a command nor worth logging`() {
        val heard = heard("[unk]" to 1.0)
        assertFalse(VoicePhrases.isOpenCommand(heard))
        assertFalse(VoicePhrases.isNearMiss(heard))
    }

    @Test
    fun `silence and malformed results are ignored`() {
        assertNull(VoicePhrases.parse("""{"text":""}"""))
        assertNull(VoicePhrases.parse("not json"))
    }

    @Test
    fun `grammar lists the open phrases and the unknown word`() {
        assertEquals("[\"open garage door\", \"open the garage door\", \"[unk]\"]", VoicePhrases.GRAMMAR)
    }

    private fun heard(vararg words: Pair<String, Double>): HeardPhrase {
        val json = words.joinToString(",") { (word, conf) -> """{"conf":$conf,"word":"$word"}""" }
        val text = words.joinToString(" ") { it.first }
        return checkNotNull(VoicePhrases.parse("""{"result":[$json],"text":"$text"}"""))
    }
}
