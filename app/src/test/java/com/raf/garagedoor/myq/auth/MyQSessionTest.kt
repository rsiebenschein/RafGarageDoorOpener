package com.raf.garagedoor.myq.auth

import com.raf.garagedoor.myq.MyQAuthenticationException
import com.raf.garagedoor.myq.MyQInvalidCallbackException
import com.raf.garagedoor.myq.MyQNotSignedInException
import com.raf.garagedoor.myq.MyQTestHarness
import com.raf.garagedoor.myq.MyQTestHarness.Companion.json
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyQSessionTest {

    private val server = MockWebServer()
    private val harness = MyQTestHarness(server)

    @After
    fun shutDownServer() = server.shutdown()

    @Test
    fun completeSignIn_mintsAppCheckTokenThenExchangesCodeWithVerifier() = runTest {
        val authorizeUrl = harness.session.beginSignIn()
        server.enqueue(json("""{"token": "app-check-token", "ttl": "3600s"}"""))
        server.enqueue(json("""{"access_token": "access-1", "refresh_token": "refresh-1", "expires_in": 3600}"""))

        harness.session.completeSignIn("com.myqops://android?code=auth-code-1&iss=x")

        val appCheckRequest = server.takeRequest()
        assertTrue(appCheckRequest.path!!.contains(":exchangeDebugToken?key="))
        val tokenRequest = server.takeRequest()
        val form = tokenRequest.body.readUtf8()
        assertEquals("app-check-token", tokenRequest.getHeader("Firebase-AppCheck-Token"))
        assertTrue(form.contains("grant_type=authorization_code") && form.contains("code=auth-code-1"))
        assertTrue(authorizeUrl.contains("code_challenge=") && form.contains("code_verifier="))
        assertEquals(MyQTestHarness.NOW.epochSecond + 3600, harness.tokenStore.tokens?.expiresAtEpochSecond)
        assertEquals(true, harness.session.isSignedIn.value)
    }

    @Test
    fun completeSignIn_rejectsCallbackWhenSignInWasNotStarted() = runTest {
        val error = runCatching { harness.session.completeSignIn("com.myqops://android?code=abc") }.exceptionOrNull()

        assertTrue(error is MyQInvalidCallbackException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun validAccessToken_refreshesExpiringTokenAndKeepsRefreshTokenWhenOmitted() = runTest {
        harness.tokenStore.tokens = OAuthTokens("old-access", "refresh-1", MyQTestHarness.NOW.epochSecond + 30)
        server.enqueue(json("""{"access_token": "new-access", "expires_in": 3600}"""))

        assertEquals("new-access", harness.session.validAccessToken())

        val form = server.takeRequest().body.readUtf8()
        assertTrue(form.contains("grant_type=refresh_token") && form.contains("refresh_token=refresh-1"))
        assertEquals("refresh-1", harness.tokenStore.tokens?.refreshToken)
    }

    @Test
    fun validAccessToken_signsOutWhenRefreshTokenIsRejected() = runTest {
        harness.tokenStore.tokens = OAuthTokens("old-access", "revoked", MyQTestHarness.NOW.epochSecond)
        server.enqueue(json("""{"error": "invalid_grant"}""", status = 400))

        val error = runCatching { harness.session.validAccessToken() }.exceptionOrNull()

        assertTrue(error is MyQAuthenticationException)
        assertNull(harness.tokenStore.tokens)
        assertEquals(false, harness.session.isSignedIn.value)
    }

    @Test
    fun validAccessToken_returnsUnexpiredTokenWithoutNetwork() = runTest {
        harness.tokenStore.tokens = MyQTestHarness.validTokens()

        assertEquals("access-1", harness.session.validAccessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun validAccessToken_withoutStoredSessionThrowsNotSignedIn() = runTest {
        val error = runCatching { harness.session.validAccessToken() }.exceptionOrNull()

        assertTrue(error is MyQNotSignedInException)
    }
}
