package com.raf.garagedoor.myq.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PkceChallengeTest {

    @Test
    fun challengeFor_matchesRfc7636AppendixBExample() {
        val challenge = PkceChallenge.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun generate_producesUnpaddedUrlSafeVerifierWithMatchingChallenge() {
        val pkce = PkceChallenge.generate()

        assertEquals(43, pkce.verifier.length)
        assertEquals(PkceChallenge.challengeFor(pkce.verifier), pkce.challenge)
        assertEquals(false, pkce.verifier.contains('=') || pkce.verifier.contains('+') || pkce.verifier.contains('/'))
    }

    @Test
    fun generate_producesDifferentVerifiersEachTime() {
        assertNotEquals(PkceChallenge.generate().verifier, PkceChallenge.generate().verifier)
    }
}
