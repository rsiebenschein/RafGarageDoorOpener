package com.raf.garagedoor.myq.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 proof key pair: the challenge goes in the authorize URL, the verifier in the token exchange. */
data class PkceChallenge(val verifier: String, val challenge: String) {

    override fun toString(): String = "PkceChallenge(challenge=$challenge)"

    companion object {
        private const val VERIFIER_BYTE_COUNT = 32
        private val base64Url = Base64.getUrlEncoder().withoutPadding()

        fun generate(random: SecureRandom = SecureRandom()): PkceChallenge {
            val verifierBytes = ByteArray(VERIFIER_BYTE_COUNT).also(random::nextBytes)
            val verifier = base64Url.encodeToString(verifierBytes)
            return PkceChallenge(verifier, challengeFor(verifier))
        }

        fun challengeFor(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return base64Url.encodeToString(digest)
        }
    }
}
