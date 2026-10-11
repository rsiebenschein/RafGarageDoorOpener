package com.raf.garagedoor.automation.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class HeardWord(val word: String, val confidence: Double)

/** What the recognizer heard, word by word with its confidence (0 to 1). */
data class HeardPhrase(val text: String, val words: List<HeardWord>)

/**
 * The recognizer only knows the words of the open phrases plus "[unk]" for anything else, so
 * ordinary speech mostly comes out as "[unk]". It does not hold to whole phrases, though: on the
 * phone, "Open garage door" came out as "[unk] open garage door" and "Is the door open" as
 * "[unk] the door open". So a command needs one open phrase as consecutive words, each heard with
 * reasonable confidence; the words around it do not matter.
 */
object VoicePhrases {
    val OPEN_PHRASES = listOf("open garage door", "open the garage door")
    const val MIN_WORD_CONFIDENCE = 0.6

    /** The recognizer's grammar, as the JSON list Vosk expects. */
    val GRAMMAR: String = (OPEN_PHRASES + UNKNOWN).joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

    private const val UNKNOWN = "[unk]"
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses a Vosk result such as {"result":[{"conf":0.98,"word":"open"},...],"text":"open garage door"}. */
    fun parse(resultJson: String): HeardPhrase? = try {
        val result = json.decodeFromString(VoskResult.serializer(), resultJson)
        result.text.takeIf { it.isNotBlank() }?.let { text -> HeardPhrase(text, result.result.map { HeardWord(it.word, it.conf) }) }
    } catch (malformed: SerializationException) {
        null
    } catch (invalid: IllegalArgumentException) {
        null
    }

    /** The lowest word confidence within the first open phrase heard, or null when none was heard. */
    fun openPhraseConfidence(heard: HeardPhrase): Double? =
        OPEN_PHRASES.firstNotNullOfOrNull { phrase -> confidenceOfPhrase(phrase.split(" "), heard.words) }

    fun isOpenCommand(heard: HeardPhrase): Boolean = (openPhraseConfidence(heard) ?: 0.0) >= MIN_WORD_CONFIDENCE

    /** Worth a Diagnostics line: anything mentioning the garage, so the threshold can be tuned. */
    fun isNearMiss(heard: HeardPhrase): Boolean = heard.words.any { it.word == "garage" }

    private fun confidenceOfPhrase(phrase: List<String>, words: List<HeardWord>): Double? =
        words.windowed(phrase.size).firstOrNull { window -> window.map { it.word } == phrase }?.minOf { it.confidence }

    @Serializable
    private data class VoskResult(val text: String = "", val result: List<VoskWord> = emptyList())

    @Serializable
    private data class VoskWord(val word: String = "", val conf: Double = 0.0)
}
