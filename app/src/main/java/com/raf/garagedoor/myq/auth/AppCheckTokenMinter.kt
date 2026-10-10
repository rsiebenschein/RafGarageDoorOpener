package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQApiException
import com.raf.garagedoor.myq.MyQAppCheckException
import com.raf.garagedoor.myq.config.MyQApiConfig
import com.raf.garagedoor.myq.http.MyQHttpExecutor
import com.raf.garagedoor.myq.http.MyQJson
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Mints the Firebase App Check token that myQ requires only for the initial authorization-code exchange. */
class AppCheckTokenMinter(
    private val config: MyQApiConfig,
    private val http: MyQHttpExecutor,
) {
    suspend fun mintToken(): String {
        val profile = AppCheckProfile.decode(config.appCheck)
        val body = try {
            http.execute(buildExchangeRequest(profile), sessionFailureStatuses = emptySet())
        } catch (error: MyQApiException) {
            throw MyQAppCheckException("Firebase App Check refused the token exchange (HTTP ${error.statusCode})")
        }
        return MyQJson.decode<AppCheckTokenResponse>(body).token.takeIf { it.isNotBlank() }
            ?: throw MyQAppCheckException("Firebase App Check returned an empty token")
    }

    private fun buildExchangeRequest(profile: AppCheckProfile): Request {
        val url = "${config.firebaseAppCheckBaseUrl}/${profile.exchangeResourcePath}".toHttpUrl().newBuilder()
            .addQueryParameter("key", profile.apiKey)
            .build()
        val payload = MyQJson.json.encodeToString(
            AppCheckExchangeRequest.serializer(),
            AppCheckExchangeRequest(profile.debugToken),
        )
        return Request.Builder()
            .url(url)
            .header("X-Android-Package", profile.androidPackage)
            .header("X-Android-Cert", profile.androidCertificate)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    @Serializable
    private data class AppCheckExchangeRequest(val debugToken: String)

    @Serializable
    private data class AppCheckTokenResponse(val token: String = "")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
