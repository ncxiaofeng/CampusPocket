package com.ecut.campuspocket.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigClaimTest {

    private val sampleGetUrl =
        "https://demo-claim-1234567890.cos.ap-shanghai.myqcloud.com/claim/abc123def456.json" +
            "?q-sign-algorithm=sha1&q-ak=DEMOKEY0000&q-sign-time=1700000000;1700003600" +
            "&q-key-time=1700000000;1700003600&q-header-list=host&q-url-param-list=" +
            "&q-signature=1111111111111111111111111111111111111111"

    private val sampleDeleteSig = "2222222222222222222222222222222222222222"

    @Test
    fun `parses valid once claim and derives delete url from ds`() {
        val raw = """{"cp_claim":1,"g":"$sampleGetUrl","m":"once","exp":1700003600,"ds":"$sampleDeleteSig"}"""
        val claim = RemoteConfigClaimParser.parse(raw)
        assertNotNull(claim)
        requireNotNull(claim)
        assertEquals(RemoteClaimMode.ONCE, claim.mode)
        assertEquals(sampleGetUrl, claim.getUrl)
        assertEquals(1700003600L, claim.expiresAtEpochSec)
        assertNotNull(claim.deleteUrl)
        assertTrue(claim.deleteUrl!!.contains("q-signature=$sampleDeleteSig"))
        assertFalse(claim.deleteUrl!!.contains("1111111111111111111111111111111111111111"))
        assertTrue(claim.deleteUrl!!.contains("q-key-time=1700000000;1700003600"))
    }

    @Test
    fun `parses valid group claim without delete url`() {
        val raw = """{"cp_claim":1,"g":"$sampleGetUrl","m":"group","exp":1700003600}"""
        val claim = RemoteConfigClaimParser.parse(raw)
        assertNotNull(claim)
        requireNotNull(claim)
        assertEquals(RemoteClaimMode.GROUP, claim.mode)
        assertNull(claim.deleteUrl)
    }

    @Test
    fun `rejects non-https and non-cos host and non-claim path`() {
        val httpUrl = sampleGetUrl.replaceFirst("https://", "http://")
        assertNull(RemoteConfigClaimParser.parse("""{"cp_claim":1,"g":"$httpUrl","m":"once"}"""))

        val evilHost = "https://evil.example.com/claim/a.json?q-signature=$sampleDeleteSig"
        assertNull(RemoteConfigClaimParser.parse("""{"cp_claim":1,"g":"$evilHost","m":"once"}"""))

        val suffixTrick = "https://myqcloud.com/claim/a.json?q-signature=$sampleDeleteSig"
        assertNull(RemoteConfigClaimParser.parse("""{"cp_claim":1,"g":"$suffixTrick","m":"once"}"""))

        val wrongPath = "https://demo-claim-1234567890.cos.ap-shanghai.myqcloud.com/other/a.json?q-signature=$sampleDeleteSig"
        assertNull(RemoteConfigClaimParser.parse("""{"cp_claim":1,"g":"$wrongPath","m":"once"}"""))

        val noSig = "https://demo-claim-1234567890.cos.ap-shanghai.myqcloud.com/claim/a.json"
        assertNull(RemoteConfigClaimParser.parse("""{"cp_claim":1,"g":"$noSig","m":"once"}"""))
    }

    @Test
    fun `rejects wrong version and ignores invalid ds`() {
        val badVer = """{"cp_claim":2,"g":"$sampleGetUrl","m":"once","ds":"$sampleDeleteSig"}"""
        assertNull(RemoteConfigClaimParser.parse(badVer))

        val plainConfig = """{"e":"abc","h":"def"}"""
        assertNull(RemoteConfigClaimParser.parse(plainConfig))

        val badDs = """{"cp_claim":1,"g":"$sampleGetUrl","m":"once","ds":"not-40-hex"}"""
        val parsed = RemoteConfigClaimParser.parse(badDs)
        assertNotNull(parsed)
        assertNull(parsed!!.deleteUrl)
    }
}
