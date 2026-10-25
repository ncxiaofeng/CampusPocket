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
}
