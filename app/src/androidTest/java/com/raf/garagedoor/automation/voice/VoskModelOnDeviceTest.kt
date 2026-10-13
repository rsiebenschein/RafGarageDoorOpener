package com.raf.garagedoor.automation.voice

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raf.garagedoor.door.DoorCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads the bundled speech model and builds the grammar recognizer on a real phone, without the
 * microphone: proves the native library and the model assets work before anyone relies on voice.
 * Run with installDebugAndroidTest and `adb shell am instrument` (connectedAndroidTest would
 * uninstall the app and wipe its sign-in and settings).
 */
@RunWith(AndroidJUnit4::class)
class VoskModelOnDeviceTest {

    @Test
    fun modelLoadsAndRecognizerAcceptsTheGrammar() {
        val model = unpackModel()
        Recognizer(model, SAMPLE_RATE, VoicePhrases.GRAMMAR).use { recognizer -> assertNotNull(recognizer.finalResult) }
        model.close()
    }

    /** Synthetic speech (macOS `say`); real voices in wind differ, so field results go to Diagnostics. */
    @Test
    fun onlyThePhrasesActAndEachDoesTheRightThing() {
        val model = unpackModel()
        val commands = EXPECTED.keys.associateWith { sample -> heardIn(model, sample)?.let(VoicePhrases::commandIn) }
        model.close()
        assertEquals(EXPECTED, commands)
    }

    /** As from the microphone: small chunks, so the recognizer ends an utterance at the pause. */
    @Test
    fun phraseSplitByAPauseActsOnceJoined() {
        val model = unpackModel()
        val commands = SPLIT_SAMPLES.keys.associateWith { sample -> commandFromStream(model, sample) }
        model.close()
        assertEquals(SPLIT_SAMPLES, commands)
    }

    private fun commandFromStream(model: Model, sample: String): DoorCommand? {
        val results = Recognizer(model, SAMPLE_RATE, VoicePhrases.GRAMMAR).use { recognizer ->
            recognizer.setWords(true)
            resultsInChunks(recognizer, audioOf(sample)) + recognizer.finalResult
        }.mapNotNull(VoicePhrases::parse)
        Log.i(LOG_TAG, "$sample streamed -> ${results.map { it.text }}")
        val joiner = SplitPhraseJoiner()
        val now = java.time.Instant.now()
        return results.firstNotNullOfOrNull { heard -> VoicePhrases.commandIn(joiner.withRecent(heard, now)) }
    }

    private fun resultsInChunks(recognizer: Recognizer, audio: ByteArray): List<String> =
        audio.toList().chunked(CHUNK_BYTES).mapNotNull { chunk ->
            val bytes = chunk.toByteArray()
            if (recognizer.acceptWaveForm(bytes, bytes.size)) recognizer.result else null
        }

    private fun audioOf(sample: String): ByteArray {
        val pcm = InstrumentationRegistry.getInstrumentation().context.assets.open("voice-samples/$sample.wav").use { it.readBytes() }
        return pcm.copyOfRange(dataChunkStart(pcm), pcm.size)
    }

    private fun heardIn(model: Model, sample: String): HeardPhrase? {
        val pcm = InstrumentationRegistry.getInstrumentation().context.assets.open("voice-samples/$sample.wav").use { it.readBytes() }
        val audio = pcm.copyOfRange(dataChunkStart(pcm), pcm.size)
        return Recognizer(model, SAMPLE_RATE, VoicePhrases.GRAMMAR).use { recognizer ->
            recognizer.setWords(true)
            recognizer.acceptWaveForm(audio, audio.size)
            VoicePhrases.parse(recognizer.finalResult).also { Log.i(LOG_TAG, "$sample -> $it") }
        }
    }

    /** WAV files from afconvert carry extra chunks, so find the "data" chunk instead of assuming 44 bytes. */
    private fun dataChunkStart(wav: ByteArray): Int {
        val marker = "data".toByteArray()
        val index = (0..wav.size - marker.size).first { i -> marker.indices.all { wav[i + it] == marker[it] } }
        return index + DATA_HEADER_BYTES
    }

    private fun unpackModel(): Model {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val done = CountDownLatch(1)
        var model: Model? = null
        var failure: IOException? = null
        StorageService.unpack(context, "vosk-model-en-us", "vosk-model", { model = it; done.countDown() }, { failure = it; done.countDown() })
        done.await(UNPACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertNull(failure)
        return checkNotNull(model)
    }

    private companion object {
        const val UNPACK_TIMEOUT_SECONDS = 120L
        const val SAMPLE_RATE = 16_000f
        const val DATA_HEADER_BYTES = 8
        const val LOG_TAG = "VoskSample"
        const val CHUNK_BYTES = 3_200 // 0.1 s of 16 kHz 16-bit audio
        val SPLIT_SAMPLES: Map<String, DoorCommand?> = mapOf(
            "open_the_pause_garage" to DoorCommand.OPEN,
            "close_the_pause_garage_door" to DoorCommand.CLOSE,
        )
        /** Sample file name to the command it should give; null means it must do nothing. */
        val EXPECTED: Map<String, DoorCommand?> = mapOf(
            "open_garage_door" to DoorCommand.OPEN,
            "open_the_garage_door" to DoorCommand.OPEN,
            "close_garage_door" to DoorCommand.CLOSE,
            "close_the_garage_door" to DoorCommand.CLOSE,
            "open_the_garage" to DoorCommand.OPEN,
            "open_garage" to DoorCommand.OPEN,
            "close_the_garage" to DoorCommand.CLOSE,
            "the_garage_is_open" to null,
            "garage_door_closed" to null,
            "open_the_window" to null,
            "garage_sale" to null,
            "door_open" to null,
        )
    }
}
