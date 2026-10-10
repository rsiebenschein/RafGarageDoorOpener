package com.raf.garagedoor.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.myq.auth.MyQSession
import com.raf.garagedoor.ui.appContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionStatus { CHECKING, SIGNED_OUT, SIGNED_IN }

data class SessionUiState(
    val status: SessionStatus = SessionStatus.CHECKING,
    val isCompletingSignIn: Boolean = false,
    val errorMessage: String? = null,
)

class SessionViewModel(private val session: MyQSession) : ViewModel() {

    private val mutableState = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = mutableState.asStateFlow()

    private val signInRequests = Channel<String>(Channel.BUFFERED)

    /** Authorization URLs the UI should open in a Custom Tab. */
    val signInUrls: Flow<String> = signInRequests.receiveAsFlow()

    init {
        observeSession()
        viewModelScope.launch { session.restore() }
    }

    fun startSignIn() {
        mutableState.update { it.copy(errorMessage = null) }
        viewModelScope.launch { signInRequests.send(session.beginSignIn()) }
    }

    fun completeSignIn(callbackUri: String) {
        viewModelScope.launch { runSignInCompletion(callbackUri) }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private fun observeSession() {
        viewModelScope.launch {
            session.isSignedIn.filterNotNull().collect { signedIn ->
                val status = if (signedIn) SessionStatus.SIGNED_IN else SessionStatus.SIGNED_OUT
                mutableState.update { it.copy(status = status) }
            }
        }
    }

    private suspend fun runSignInCompletion(callbackUri: String) {
        mutableState.update { it.copy(isCompletingSignIn = true, errorMessage = null) }
        try {
            session.completeSignIn(callbackUri)
        } catch (error: GarageDoorControlException) {
            mutableState.update { it.copy(errorMessage = error.message) }
        } finally {
            mutableState.update { it.copy(isCompletingSignIn = false) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SessionViewModel(appContainer().myQSession) }
        }
    }
}
