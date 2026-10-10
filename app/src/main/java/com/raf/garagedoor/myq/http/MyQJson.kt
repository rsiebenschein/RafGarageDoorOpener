package com.raf.garagedoor.myq.http

import com.raf.garagedoor.myq.MyQResponseFormatException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object MyQJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Decodes [body], converting malformed or unexpected JSON into a [MyQResponseFormatException]. */
    inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString(serializer<T>(), body)
    } catch (error: IllegalArgumentException) {
        throw MyQResponseFormatException("myQ returned an unexpected response", error)
    }
}
