package com.raf.garagedoor.automation.voice

import android.content.Context

/** Lets screens start or stop [VoiceCommandService] without holding a Context themselves. */
class VoiceServiceControl(private val context: Context) {
    fun start() = VoiceCommandService.start(context)

    fun stop() = VoiceCommandService.stop(context)
}
