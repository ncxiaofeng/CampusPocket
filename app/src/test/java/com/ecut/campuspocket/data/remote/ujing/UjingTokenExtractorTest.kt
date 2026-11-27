package com.ecut.campuspocket.data.remote.ujing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UjingTokenExtractorTest {

    private val sampleJwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOjEyMzQ1Njc4OX0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    @Test
    fun `extracts valid jwt from quoted evaluateJavascript result`() {
        assertEquals(sampleJwt, UjingTokenExtractor.extractJwtFromJsResult("\"$sampleJwt\""))
        assertEquals(sampleJwt, UjingTokenExtractor.extractJwtFromJsResult("\"Bearer $sampleJwt\""))
        assertEquals(sampleJwt, UjingTokenExtractor.sanitizeJwt("bearer $sampleJwt"))
    }

    @Test
    fun `rejects empty null and malformed jwt candidates`() {
        assertNull(UjingTokenExtractor.extractJwtFromJsResult(null))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("null"))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("\"\""))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("\"null\""))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("\"undefined\""))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("\"not-a-jwt\""))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("\"part1.part2\""))
        assertNull(UjingTokenExtractor.extractJwtFromJsResult("\"abc.def.ghi\""))
    }
}
