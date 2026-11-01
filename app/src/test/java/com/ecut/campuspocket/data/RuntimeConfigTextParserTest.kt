package com.ecut.campuspocket.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeConfigTextParserTest {

    @Test
    fun parsesKeyValueLines() {
        val values = RuntimeConfigTextParser.parse(
            """
            # comment
            electricity.operator.pwd=a+b
            ujing.hmac.key=key123
            huilife.app.sign.salt=salt456
            unrelated=value
            """.trimIndent()
        )
        assertEquals("a+b", values[RuntimeConfigTextParser.KEY_ELECTRICITY])
        assertEquals("key123", values[RuntimeConfigTextParser.KEY_UJING])
        assertEquals("salt456", values[RuntimeConfigTextParser.KEY_HUILIFE])
        assertEquals(3, values.size)
    }

    @Test
    fun parsesJsonShortKeys() {
        val values = RuntimeConfigTextParser.parse(
            """{"e":"pwd","u":"key","h":"salt"}"""
        )
        assertEquals("pwd", values[RuntimeConfigTextParser.KEY_ELECTRICITY])
        assertEquals("key", values[RuntimeConfigTextParser.KEY_UJING])
        assertEquals("salt", values[RuntimeConfigTextParser.KEY_HUILIFE])
    }

    @Test
    fun ignoresBlankValues() {
        val values = RuntimeConfigTextParser.parse(
            "electricity.operator.pwd=\nujing.hmac.key=abc"
        )
        assertEquals(1, values.size)
        assertEquals("abc", values[RuntimeConfigTextParser.KEY_UJING])
    }
}
