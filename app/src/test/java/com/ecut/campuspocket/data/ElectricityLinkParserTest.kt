package com.ecut.campuspocket.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectricityLinkParserTest {

    @Test
    fun extractsFromQuery() {
        val raw = "https://xgwx.ecut.edu.cn/app/wechat/index.jsp" +
            "?appId=wx3be3089d48d67c4e&operatorId=admin&operatorPwd=a%2Bb%20c"
        assertEquals("a+b c", ElectricityLinkParser.extractOperatorPwd(raw))
    }

    @Test
    fun extractsFromFragment() {
        val raw = "https://example.com/h5#/pay?operatorpwd=secret123&x=1"
        assertEquals("secret123", ElectricityLinkParser.extractOperatorPwd(raw))
    }

    @Test
    fun ignoresOtherLinks() {
        assertNull(ElectricityLinkParser.extractOperatorPwd("https://example.com/?id=1"))
        assertNull(ElectricityLinkParser.extractOperatorPwd("没有链接"))
    }
}
