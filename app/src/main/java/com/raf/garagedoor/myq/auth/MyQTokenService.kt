package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQResponseFormatException
import com.raf.garagedoor.myq.config.MyQApiConfig
import com.raf.garagedoor.myq.http.MyQHttpExecutor
import com.raf.garagedoor.myq.http.MyQJson
import com.raf.garagedoor.myq.http.MyQRequestHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import java.time.Clock

class MyQTokenService(
    private val config: MyQApiConfig,
    private val http: MyQHttpExecutor,
    private val headers: MyQRequestHeaders,
    private val appCheckTokenMinter: AppCheckTokenMinter,
    private val clock: Clock,
) {
    suspend fun exchangeAuthorizationCode(code: String, verifier: String): OAuthTokens {
        val appCheckToken = appCheckTokenMinter.mintToken()
        val form = baseForm()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", verifier)
            .build()
        return requestTokens(form, appCheckToken, fallbackRefreshToken = null)
    }

    suspend fun refresh(tokens: OAuthTokens): OAuthTokens {
        val form = baseForm()
            .add("grant_type", "refresh_token")
            .add("refresh_token", tokens.refreshToken)
            .build()
        return requestTokens(form, appCheckToken = null, fallbackRefreshToken = tokens.refreshToken)
    }

    private fun baseForm(): FormBody.Builder = FormBody.Builder()
        .add("client_id", config.oauthClientId)
        .add("scope", config.oauthScope)
        .add("redirect_uri", config.oauthRedirectUri)

    private suspend fun requestTokens(form: FormBody, appCheckToken: String?, fallbackRefreshToken: String?): OAuthTokens {
        val requestHeaders = headers.tokenEndpoint().newBuilder()
            .apply { appCheckToken?.let { add("Firebase-AppCheck-Token", it) } }
            .build()
        val request = Request.Builder()
            .url("${config.identityBaseUrl}/connect/token")
            .headers(requestHeaders)
            .post(form)
            .build()
        val body = http.execute(request, TOKEN_SESSION_FAILURE_STATUSES)
        return toTokens(MyQJson.decode<TokenResponse>(body), fallbackRefreshToken)
    }

    private fun toTokens(response: TokenResponse, fallbackRefreshToken: String?): OAuthTokens {
        val refreshToken = response.refreshToken ?: fallbackRefreshToken
            ?: throw MyQResponseFormatException("myQ returned no refresh token")
        val expiresAt = clock.instant().epochSecond + response.expiresInSeconds.toLong()
        return OAuthTokens(response.accessToken, refreshToken, expiresAt)
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresInSeconds: Double,
    )

    private companion object {
        /** The token endpoint answers 400 (invalid_grant) when a refresh token is revoked or expired. */
        val TOKEN_SESSION_FAILURE_STATUSES = setOf(400, 401, 403)
    }
}
