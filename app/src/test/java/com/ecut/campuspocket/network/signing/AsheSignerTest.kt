package com.ecut.campuspocket.network.signing

import org.junit.Assert.assertEquals
import org.junit.Test

class AsheSignerTest {

    @Test
    fun `signature matches the documented formula`() {
        val signature = AsheSigner.sign(
            servername = "demo",
            methodname = "getDevice",
            timestamp = "1700000000",
            randomStr = "123456"
        )
        assertEquals("0d5bb0b7660acb98d504981503399765", signature)
    }

    @Test
    fun `anonymous nami is seventeen characters`() {
        val nami = AsheSigner.anonymousNami()
        assertEquals(17, nami.length)
        assertEquals(true, nami.all { it.isLetterOrDigit() })
    }
}
