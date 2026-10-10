package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQInvalidCallbackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthorizationCallbackParserTest {

    private val redirectUri = "com.myqops://android"

    @Test
    fun extractCode_readsCodeFromRealCallbackShape() {
        val callback = "com.myqops://android?code=6ADF3F28A9D9-1&iss=https%3A%2F%2Fpartner-identity.myq-cloud.com"

        assertEquals("6ADF3F28A9D9-1", AuthorizationCallbackParser.extractCode(callback, redirectUri))
    }

    @Test
    fun extractCode_decodesPercentEncodedCode() {
        val callback = "com.myqops://android?code=abc%2Fdef&scope=MyQ_Residential%20offline_access"

        assertEquals("abc/def", AuthorizationCallbackParser.extractCode(callback, redirectUri))
    }

    @Test
    fun extractCode_rejectsCallbackCarryingAnError() {
        val callback = "com.myqops://android?error=access_denied"

        val error = assertThrows(MyQInvalidCallbackException::class.java) {
            AuthorizationCallbackParser.extractCode(callback, redirectUri)
        }
        assertEquals("myQ sign-in failed: access_denied", error.message)
    }

    @Test
    fun extractCode_rejectsCallbackWithoutCode() {
        assertThrows(MyQInvalidCallbackException::class.java) {
            AuthorizationCallbackParser.extractCode("com.myqops://android?iss=x", redirectUri)
        }
    }

    @Test
    fun extractCode_rejectsForeignRedirect() {
        assertThrows(MyQInvalidCallbackException::class.java) {
            AuthorizationCallbackParser.extractCode("https://evil.example/?code=abc", redirectUri)
        }
    }
}
