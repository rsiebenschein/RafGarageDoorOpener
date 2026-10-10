package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQInvalidCallbackException
import java.net.URLDecoder

/** Pulls the authorization code out of the com.myqops://android?code=... redirect. */
object AuthorizationCallbackParser {

    fun extractCode(callbackUri: String, expectedRedirectUri: String): String {
        if (!callbackUri.startsWith(expectedRedirectUri)) {
            throw MyQInvalidCallbackException("Unexpected sign-in callback address")
        }
        val parameters = parseQuery(callbackUri.substringAfter('?', missingDelimiterValue = ""))
        parameters["error"]?.let { throw MyQInvalidCallbackException("myQ sign-in failed: $it") }
        return parameters["code"]?.takeIf { it.isNotBlank() }
            ?: throw MyQInvalidCallbackException("The sign-in callback has no authorization code")
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .filter { it.contains('=') }
        .associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            decode(key) to decode(value)
        }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)
}
