package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.door.DoorCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class HeardWord(val word: String, val confidence: Double)

/** What the recognizer heard, word by word with its confidence (0 to 1). */
data class HeardPhrase(val text: String, val words: List<HeardWord>)

/** A door command found in what was heard, with the lowest confidence among the phrase's words. */
data class VoiceCommand(val command: DoorCommand, val confidence: Double)

/**
 * The recognizer only knows the words of the command phrases plus "[unk]" for anything else, so
 * ordinary speech mostly comes out as "[unk]". It does not hold to whole phrases, though: on the
 * phone, "Open garage door" came out as "[unk] open garage door" and "Is the door open" as
 * "[unk] the door open". So a command needs one phrase as consecutive words, each heard with
 * reasonable confidence; the words around it do not matter. "Open the garage" (no "door") is what
 * came naturally in field test 5, so "door" and "the" are both optional.
 */
object VoicePhrases {
    /** Longest first, so the confidence logged covers every word said. "door" is optional (field test 5). */
    val PHRASES: Map<String, DoorCommand> = mapOf(
        "open the garage door" to DoorCommand.OPEN,
        "open garage door" to DoorCommand.OPEN,
        "open the garage" to DoorCommand.OPEN,
        "open garage" to DoorCommand.OPEN,
        "close the garage door" to DoorCommand.CLOSE,
        "close garage door" to DoorCommand.CLOSE,
        "close the garage" to DoorCommand.CLOSE,
        "close garage" to DoorCommand.CLOSE,
    )
    const val MIN_WORD_CONFIDENCE = 0.6

    /** The recognizer's grammar, as the JSON list Vosk expects. */
    val GRAMMAR: String = (PHRASES.keys + UNKNOWN).joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

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

    /** The first command phrase heard, whatever its confidence; null when none was heard. */
    fun phraseIn(heard: HeardPhrase): VoiceCommand? = PHRASES.entries.firstNotNullOfOrNull { (phrase, command) ->
        confidenceOfPhrase(phrase.split(" "), heard.words)?.let { VoiceCommand(command, it) }
    }

    /** The command to send: a phrase heard with every word at [MIN_WORD_CONFIDENCE] or more. */
    fun commandIn(heard: HeardPhrase): DoorCommand? =
        phraseIn(heard)?.takeIf { it.confidence >= MIN_WORD_CONFIDENCE }?.command

    /** Worth a Diagnostics line: anything mentioning the garage, so the threshold can be tuned. */
    fun isNearMiss(heard: HeardPhrase): Boolean = heard.words.any { it.word == "garage" }

    private fun confidenceOfPhrase(phrase: List<String>, words: List<HeardWord>): Double? =
        words.windowed(phrase.size).firstOrNull { window -> window.map { it.word } == phrase }?.minOf { it.confidence }

    @Serializable
    private data class VoskResult(val text: String = "", val result: List<VoskWord> = emptyList())

    @Serializable
    private data class VoskWord(val word: String = "", val conf: Double = 0.0)
}
