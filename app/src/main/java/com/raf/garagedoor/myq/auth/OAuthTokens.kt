package com.raf.garagedoor.myq.auth

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

@Serializable
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSecond: Long,
) {
    fun isExpiringWithin(margin: Duration, now: Instant): Boolean =
        now.plus(margin).epochSecond >= expiresAtEpochSecond

    override fun toString(): String = "OAuthTokens(expiresAtEpochSecond=$expiresAtEpochSecond)"
}
