package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQAuthenticationException
import com.raf.garagedoor.myq.MyQInvalidCallbackException
import com.raf.garagedoor.myq.MyQNotSignedInException
import com.raf.garagedoor.myq.config.MyQApiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

/** Owns the myQ OAuth session: sign-in, token persistence, and refresh serialized behind a mutex. */
class MyQSession(
    private val config: MyQApiConfig,
    private val tokenService: MyQTokenService,
    private val tokenStore: TokenStore,
    private val authorizationUrlFactory: AuthorizationUrlFactory,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private var cachedTokens: OAuthTokens? = null
    private val signedIn = MutableStateFlow<Boolean?>(null)

    /** `null` until [restore] has checked storage. */
    val isSignedIn: StateFlow<Boolean?> = signedIn.asStateFlow()

    suspend fun restore() {
        signedIn.value = mutex.withLock { currentTokens() } != null
    }

    suspend fun beginSignIn(): String {
        val challenge = PkceChallenge.generate()
        tokenStore.savePendingVerifier(challenge.verifier)
        return authorizationUrlFactory.create(challenge)
    }

    suspend fun completeSignIn(callbackUri: String) {
        val code = AuthorizationCallbackParser.extractCode(callbackUri, config.oauthRedirectUri)
        val verifier = tokenStore.takePendingVerifier()
            ?: throw MyQInvalidCallbackException("This sign-in was not started from the app; please try again")
        val tokens = tokenService.exchangeAuthorizationCode(code, verifier)
        mutex.withLock { persist(tokens) }
        signedIn.value = true
    }

    suspend fun validAccessToken(): String = mutex.withLock {
        val tokens = currentTokens() ?: throw MyQNotSignedInException()
        if (!tokens.isExpiringWithin(EXPIRY_MARGIN, clock.instant())) return@withLock tokens.accessToken
        refreshOrSignOut(tokens).accessToken
    }

    /** Refreshes even if the access token looks valid, for when the API rejected it anyway. */
    suspend fun forceRefresh(): String = mutex.withLock {
        val tokens = currentTokens() ?: throw MyQNotSignedInException()
        refreshOrSignOut(tokens).accessToken
    }

    suspend fun signOut() = mutex.withLock { clearSessionLocked() }

    private suspend fun refreshOrSignOut(tokens: OAuthTokens): OAuthTokens = try {
        tokenService.refresh(tokens).also { persist(it) }
    } catch (rejected: MyQAuthenticationException) {
        clearSessionLocked()
        throw rejected
    }

    private suspend fun currentTokens(): OAuthTokens? =
        cachedTokens ?: tokenStore.loadTokens().also { cachedTokens = it }

    private suspend fun persist(tokens: OAuthTokens) {
        cachedTokens = tokens
        tokenStore.saveTokens(tokens)
    }

    private suspend fun clearSessionLocked() {
        cachedTokens = null
        tokenStore.clear()
        signedIn.value = false
    }

    private companion object {
        val EXPIRY_MARGIN: Duration = Duration.ofMinutes(1)
    }
}
