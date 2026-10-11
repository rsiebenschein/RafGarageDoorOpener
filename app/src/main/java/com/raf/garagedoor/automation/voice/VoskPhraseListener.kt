package com.raf.garagedoor.automation.voice

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline speech recognition (Vosk) limited to [VoicePhrases.GRAMMAR]. Audio never leaves the
 * phone and is not stored. The model is copied out of the app's assets on first use (~70 MB).
 */
class VoskPhraseListener(
    private val context: Context,
    private val onPhrase: (HeardPhrase) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null

    val isListening: Boolean get() = speech != null

    /** Opens the microphone; reports a problem instead of throwing, so the service keeps running. */
    suspend fun start() {
        if (speech != null) return
        try {
            val loaded = model ?: unpackModel().also { model = it }
            val grammarRecognizer = Recognizer(loaded, SAMPLE_RATE, VoicePhrases.GRAMMAR).apply { setWords(true) }
            recognizer = grammarRecognizer
            speech = SpeechService(grammarRecognizer, SAMPLE_RATE).also { it.startListening(listener) }
        } catch (failed: IOException) {
            stop()
            onProblem("Voice listener could not start: ${failed.message}")
        }
    }

    /** Closes the microphone; the model stays loaded so the next start is quick. */
    fun stop() {
        speech?.stop()
        speech?.shutdown()
        speech = null
        recognizer?.close()
        recognizer = null
    }

    fun release() {
        stop()
        model?.close()
        model = null
    }

    private suspend fun unpackModel(): Model = suspendCancellableCoroutine { continuation ->
        StorageService.unpack(
            context,
            MODEL_ASSET_DIR,
            MODEL_TARGET_DIR,
            { loaded -> continuation.resume(loaded) },
            { failure -> continuation.resumeWithException(failure) },
        )
    }

    private val listener = object : RecognitionListener {
        override fun onResult(hypothesis: String) = reportPhrase(hypothesis)

        override fun onFinalResult(hypothesis: String) = reportPhrase(hypothesis)

        override fun onPartialResult(hypothesis: String) = Unit

        override fun onError(exception: Exception) {
            stop()
            onProblem("Voice listener stopped: ${exception.message}")
        }

        override fun onTimeout() = Unit
    }

    private fun reportPhrase(hypothesis: String) {
        VoicePhrases.parse(hypothesis)?.let(onPhrase)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000f
        const val MODEL_ASSET_DIR = "vosk-model-en-us"
        const val MODEL_TARGET_DIR = "vosk-model"
    }
}
