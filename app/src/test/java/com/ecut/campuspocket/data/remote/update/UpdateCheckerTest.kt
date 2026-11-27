package com.ecut.campuspocket.data.remote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `normalizes release tags`() {
        assertEquals("0.4.1", UpdateChecker.normalizeVersion("v0.4.1"))
        assertEquals("0.4.1", UpdateChecker.normalizeVersion("V0.4.1"))
        assertEquals("0.4.1", UpdateChecker.normalizeVersion("0.4.1-beta"))
        assertEquals("0.4.1", UpdateChecker.normalizeVersion("0.4.1+20261025"))
    }

    @Test
    fun `compares numeric versions`() {
        assertTrue(UpdateChecker.isNewer("0.4.1", "0.4.0"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.99.99"))
        assertFalse(UpdateChecker.isNewer("0.4.0", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `ignores non numeric versions`() {
        assertFalse(UpdateChecker.isNewer("latest", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("0.4.1", "unknown"))
    }

    @Test
    fun `filters out ios tags and picks latest android release`() {
        assertTrue(UpdateChecker.isAndroidReleaseTag("v0.6.4"))
        assertFalse(UpdateChecker.isAndroidReleaseTag("ios-v0.6.5"))
        assertFalse(UpdateChecker.isAndroidReleaseTag("ios-v0.6.4"))

        val listJson = """
            [
              {"tag_name":"ios-v0.6.5","draft":false,"prerelease":false,"assets":[{"name":"CampusPocket-iOS-0.6.5-unsigned.ipa"}]},
              {"tag_name":"v0.6.5","draft":true,"prerelease":false,"assets":[]},
              {"tag_name":"v0.6.4","draft":false,"prerelease":false,"assets":[{"name":"CampusPocket-0.6.4.apk"}]},
              {"tag_name":"v0.6.3","draft":false,"prerelease":false,"assets":[]}
            ]
        """.trimIndent()
        val picked = UpdateChecker.selectLatestAndroidRelease(listJson)
        assertEquals("v0.6.4", picked?.get("tag_name")?.asString)
    }
}
