package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQAppCheckException
import com.raf.garagedoor.myq.config.AppCheckProfileSecret

/** Decoded Firebase App Check registration. [toString] is redacted so it never lands in logs. */
data class AppCheckProfile(
    val projectId: String,
    val appId: String,
    val apiKey: String,
    val debugToken: String,
    val androidPackage: String,
    val androidCertificate: String,
) {
    val exchangeResourcePath: String get() = "v1/projects/$projectId/apps/$appId:exchangeDebugToken"

    override fun toString(): String = "AppCheckProfile(projectId=$projectId, package=$androidPackage)"

    companion object {
        private const val FIELD_SEPARATOR = '\u0000'
        private const val FIELD_COUNT = 6

        fun decode(secret: AppCheckProfileSecret): AppCheckProfile {
            val fields = unmask(secret).toString(Charsets.US_ASCII).split(FIELD_SEPARATOR)
            if (fields.size != FIELD_COUNT) {
                throw MyQAppCheckException("App Check profile has ${fields.size} fields, expected $FIELD_COUNT")
            }
            return AppCheckProfile(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5])
        }

        fun mask(plain: ByteArray, mask: ByteArray): ByteArray =
            ByteArray(plain.size) { index -> (plain[index].toInt() xor mask[index % mask.size].toInt()).toByte() }

        private fun unmask(secret: AppCheckProfileSecret): ByteArray {
            val masked = hexToBytes(secret.prefixHex + secret.bodyHex + secret.suffixHex)
            return mask(masked, hexToBytes(secret.maskHex))
        }

        private fun hexToBytes(hex: String): ByteArray {
            if (hex.length % 2 != 0) throw MyQAppCheckException("App Check profile hex has odd length")
            return ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }
    }
}
