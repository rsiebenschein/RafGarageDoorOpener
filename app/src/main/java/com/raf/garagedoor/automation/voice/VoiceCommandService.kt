package com.raf.garagedoor.automation.voice

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raf.garagedoor.GarageApplication
import com.raf.garagedoor.automation.AutomationNotifications
import com.raf.garagedoor.automation.DoorCommandWorker
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.location.QuickLocationReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * "Open the garage" / "Close the garage" by voice, with the phone locked and the app closed. Android
 * only lets an app use the microphone in the background from a foreground service that was started
 * while the app was on screen, so this service is started from the app and then stays running; it
 * only opens the microphone while [VoiceListeningGate] says the user is within 500 ft of the garage.
 */
class VoiceCommandService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as GarageApplication).container
    private val notifications: AutomationNotifications get() = container.automationNotifications
    private lateinit var listener: VoskPhraseListener
    private lateinit var wakeReadings: ScreenWakeReadings
    private var gateWatch: Job? = null
    private var lastSent: Pair<DoorCommand, Instant>? = null
    private val joiner = SplitPhraseJoiner()

    override fun onBind(intent: Intent?): IBinder? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wakeReadings.onScreenWoken()
        }
    }

    override fun onCreate() {
        super.onCreate()
        listener = VoskPhraseListener(this, ::onPhrase) { problem -> record(problem) }
        wakeReadings = ScreenWakeReadings(QuickLocationReader(this), container.geofenceSettings, container.presenceRepository, container.clock)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            notifications.showVoicePaused()
            stopSelf()
            return START_NOT_STICKY
        }
        notifications.dismissVoicePaused()
        wakeReadings.onScreenWoken() // started from the app, so the user just opened it
        if (gateWatch == null) gateWatch = scope.launch { followListeningGate() }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        listener.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Fails when started from the background (e.g. Android restarting the service): no microphone then. */
    private fun enterForeground(): Boolean = try {
        val notification = notifications.voiceNotification(isListening = false)
        ServiceCompat.startForeground(this, AutomationNotifications.VOICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        true
    } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
        false
    } catch (microphoneNotAllowed: SecurityException) {
        false
    }

    private suspend fun followListeningGate() {
        val settings = container.automationSettings.settings
        combine(settings, container.presenceRepository.state, wakeReadings.readings, everyFewSeconds()) { current, presence, wake, _ ->
            current.voiceCommandsEnabled to VoiceListeningGate.reasonToListen(presence, wake, container.clock.instant())
        }.collect { (isEnabled, reason) ->
            if (!isEnabled) stopSelf() else applyListening(reason)
        }
    }

    /** Re-checks the gate while nothing else changes, so listening stops once the last reading is too old. */
    private fun everyFewSeconds(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(GATE_RECHECK_MILLIS)
        }
    }

    private suspend fun applyListening(reason: VoiceListeningGate.Reason?) {
        val shouldListen = reason != null
        if (shouldListen == listener.isListening) return
        if (shouldListen) listener.start() else listener.stop()
        if (listener.isListening != shouldListen) return
        notifications.updateVoiceNotification(shouldListen)
        record(if (reason != null) "Voice: listening (within 500 ft: ${reason.label})" else "Voice: stopped listening")
    }

    private fun onPhrase(heard: HeardPhrase) {
        val match = commandMatching(heard)
        when {
            match != null -> send(match.first, match.second).also { joiner.forget() }
            VoicePhrases.isNearMiss(heard) -> record("Voice: heard \"${heard.text}\" (${confidenceLabel(heard)}), not sure enough to act")
        }
    }

    /** This result on its own first, then joined to the one just before it ([SplitPhraseJoiner]). */
    private fun commandMatching(heard: HeardPhrase): Pair<DoorCommand, HeardPhrase>? {
        val joined = joiner.withRecent(heard, container.clock.instant())
        VoicePhrases.commandIn(heard)?.let { return it to heard }
        return VoicePhrases.commandIn(joined)?.let { it to joined }
    }

    /** The same command again within [REPEAT_COOLDOWN] is an echo or a repeat; the other command always goes through. */
    private fun send(command: DoorCommand, heard: HeardPhrase) {
        val now = container.clock.instant()
        val isRepeat = lastSent?.let { (previous, at) -> previous == command && Duration.between(at, now) < REPEAT_COOLDOWN } == true
        if (isRepeat) return
        lastSent = command to now
        acknowledgeWithTone()
        DoorCommandWorker.enqueue(this, command, REASON)
        scope.launch { container.departureOnDoorOpen.onCommandAccepted(command, source = "voice") }
        record("Voice: heard \"${heard.text}\" (${confidenceLabel(heard)}), ${if (command == DoorCommand.OPEN) "opening" else "closing"}")
    }

    /** ToneGenerator reports a busy or missing audio device only as a RuntimeException; the door opens anyway. */
    private fun acknowledgeWithTone() {
        val tone = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        } catch (audioUnavailable: RuntimeException) {
            return
        }
        tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MILLIS)
        scope.launch {
            delay(TONE_MILLIS.toLong())
            tone.release()
        }
    }

    private fun confidenceLabel(heard: HeardPhrase): String {
        val confidence = VoicePhrases.phraseIn(heard)?.confidence ?: heard.words.minOfOrNull { it.confidence } ?: 0.0
        return "confidence ${String.format(Locale.US, "%.2f", confidence)}"
    }

    private fun record(message: String) {
        scope.launch { container.diagnosticsLog.record(message) }
    }

    companion object {
        private const val REASON = "Voice command"
        private const val GATE_RECHECK_MILLIS = 15_000L
        private const val TONE_VOLUME = 80
        private const val TONE_MILLIS = 300
        private val REPEAT_COOLDOWN: Duration = Duration.ofSeconds(30)

        /** Only while the app is on screen (or from a tap on its notification); see the class comment. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceCommandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceCommandService::class.java))
        }
    }
}
