package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQAppCheckException
import com.raf.garagedoor.myq.config.AppCheckProfileSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckProfileTest {

    private val mask = byteArrayOf(0x4b, 0x17, 0x7f, 0x22)

    @Test
    fun decode_unmasksSixNulSeparatedFields() {
        val profile = AppCheckProfile.decode(secretFor("proj\u0000app\u0000key\u0000token\u0000com.pkg\u0000CERT"))

        assertEquals(AppCheckProfile("proj", "app", "key", "token", "com.pkg", "CERT"), profile)
        assertEquals("v1/projects/proj/apps/app:exchangeDebugToken", profile.exchangeResourcePath)
    }

    @Test
    fun decode_rejectsProfileWithWrongFieldCount() {
        assertThrows(MyQAppCheckException::class.java) {
            AppCheckProfile.decode(secretFor("only\u0000three\u0000fields"))
        }
    }

    @Test
    fun decode_bundledProfileYieldsCompleteRegistration() {
        val profile = AppCheckProfile.decode(AppCheckProfileSecret.BUNDLED)

        listOf(profile.projectId, profile.appId, profile.apiKey, profile.debugToken, profile.androidCertificate)
            .forEach { assertTrue(it.isNotBlank()) }
        assertTrue(profile.androidPackage.contains('.'))
    }

    @Test
    fun toString_doesNotLeakSecrets() {
        val profile = AppCheckProfile("proj", "app", "secret-key", "secret-token", "com.pkg", "CERT")

        assertFalse(profile.toString().contains("secret"))
    }

    private fun secretFor(plain: String): AppCheckProfileSecret {
        val masked = AppCheckProfile.mask(plain.toByteArray(Charsets.US_ASCII), mask).toHex()
        return AppCheckProfileSecret(maskHex = mask.toHex(), prefixHex = masked.take(4), bodyHex = masked.drop(4), suffixHex = "")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
