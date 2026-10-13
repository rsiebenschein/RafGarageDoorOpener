package com.raf.garagedoor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.raf.garagedoor.automation.AwayWatchResume
import com.raf.garagedoor.automation.voice.VoiceCommandService
import com.raf.garagedoor.ui.GarageAppRoot
import com.raf.garagedoor.ui.session.SessionViewModel
import com.raf.garagedoor.ui.theme.RafsGarageTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels { SessionViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity is handed its original intent again; its callback was already consumed.
        if (savedInstanceState == null) consumeSignInCallback(intent)
        setContent { RafsGarageTheme { GarageAppRoot(sessionViewModel) } }
    }

    /**
     * The voice service may only take the microphone while the app is on screen, so (re)start it
     * here; the away watch is also brought back if it is no longer running.
     */
    override fun onStart() {
        super.onStart()
        val container = (application as GarageApplication).container
        lifecycleScope.launch {
            AwayWatchResume.resumeIfAway(this@MainActivity, container, reason = "app opened")
            val isWanted = container.automationSettings.current().voiceCommandsEnabled
            if (isWanted && container.automationPermissions.status().microphone) VoiceCommandService.start(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeSignInCallback(intent)
    }

    private fun consumeSignInCallback(intent: Intent) {
        val callback = intent.data?.takeIf { it.scheme == MYQ_CALLBACK_SCHEME } ?: return
        intent.data = null
        sessionViewModel.completeSignIn(callback.toString())
    }

    private companion object {
        const val MYQ_CALLBACK_SCHEME = "com.myqops"
    }
}
