package com.raf.garagedoor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.raf.garagedoor.ui.GarageAppRoot
import com.raf.garagedoor.ui.session.SessionViewModel
import com.raf.garagedoor.ui.theme.RafsGarageTheme

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels { SessionViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity is handed its original intent again; its callback was already consumed.
        if (savedInstanceState == null) consumeSignInCallback(intent)
        setContent { RafsGarageTheme { GarageAppRoot(sessionViewModel) } }
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
