package com.ecut.campuspocket.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasSsoManagerTest {

    @Test
    fun `detects castgc case insensitively`() {
        assertTrue(CasSsoManager.hasCasTicket("route=abc; CASTGC=TGT-1; JSESSIONID=x"))
        assertTrue(CasSsoManager.hasCasTicket("Tgc=TGT-2"))
        assertTrue(CasSsoManager.hasCasTicket("mod_auth_cas=abc"))
        assertFalse(CasSsoManager.hasCasTicket("JSESSIONID=abc; route=def"))
    }

    @Test
    fun `extracts ticket value`() {
        assertEquals("TGT-1", CasSsoManager.casTicketValue("CASTGC=TGT-1; route=x"))
        assertNull(CasSsoManager.casTicketValue("route=x"))
    }

    @Test
    fun `jw service url keeps the original double encoding`() {
        val url = CasSsoManager.buildServiceLoginUrl(CampusEndpoints.JW_SSO_URL)
        assertTrue(url.startsWith(CampusEndpoints.AUTH_SERVER_LOGIN_URL + "?service="))
        assertTrue(url.contains("jziotlogin%3Furl%3Dkbcx%252fxskbcx"))
    }

    @Test
    fun `electricity service url encodes its query`() {
        val url = CasSsoManager.buildServiceLoginUrl(CampusEndpoints.ELECTRICITY_INDEX_URL)
        assertTrue(
            url.contains(
                "service=https%3A%2F%2Fxgwx.ecut.edu.cn%2Fapp%2Fwechat%2Findex.jsp%3FappId%3D"
            )
        )
    }
}
