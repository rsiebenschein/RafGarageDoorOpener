package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.config.MyQApiConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

class AuthorizationUrlFactory(private val config: MyQApiConfig) {

    fun create(challenge: PkceChallenge): String = config.identityBaseUrl.toHttpUrl().newBuilder()
        .addPathSegments("connect/authorize")
        .addQueryParameter("acr_values", "unified_flow:v1 brand:myq")
        .addQueryParameter("client_id", config.oauthClientId)
        .addQueryParameter("code_challenge", challenge.challenge)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("ui_locales", "en-US")
        .addQueryParameter("redirect_uri", config.oauthRedirectUri)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("scope", config.oauthScope)
        .addQueryParameter("prompt", "login")
        .build()
        .toString()
}
