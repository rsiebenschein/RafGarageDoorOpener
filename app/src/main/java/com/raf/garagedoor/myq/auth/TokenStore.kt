package com.raf.garagedoor.myq.auth

interface TokenStore {
    suspend fun loadTokens(): OAuthTokens?

    suspend fun saveTokens(tokens: OAuthTokens)

    suspend fun savePendingVerifier(verifier: String)

    /** Returns the PKCE verifier saved when sign-in started and removes it, so a callback can be used only once. */
    suspend fun takePendingVerifier(): String?

    suspend fun clear()
}
