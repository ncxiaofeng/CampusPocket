package com.ecut.campuspocket.network

import com.ecut.campuspocket.data.local.CookieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CasAutoLoginAndCookieTest {

    @Test
    fun `parses cas form regardless of attribute order and quote style`() {
        val html = """
            <html><body>
            <form action="/authserver/login?service=https%3A%2F%2Fatrust1.ecut.edu.cn" id="casLoginForm" method="post">
              <input value="LT-12345" type="hidden" name="lt" />
              <input value='e1s1' name='execution' type='hidden'>
              <input type="hidden" value="userNamePasswordLogin" name="dllt">
              <input type="hidden" value="16ByteSalt123456" id="pwdDefaultEncryptSalt" />
            </form>
            </body></html>
        """.trimIndent()

        val form = CasAutoLogin.parseLoginForm(html)
        assertEquals("/authserver/login?service=https%3A%2F%2Fatrust1.ecut.edu.cn", form.action)
        assertEquals("16ByteSalt123456", form.encryptSalt)
        assertEquals("LT-12345", form.hiddenFields["lt"])
        assertEquals("e1s1", form.hiddenFields["execution"])
        assertEquals("userNamePasswordLogin", form.hiddenFields["dllt"])
    }

    @Test
    fun `parses newer cas form without lt using pwdEncryptSalt`() {
        val html = """
            <form id="pwdFromId" action="/authserver/login">
              <input type="hidden" name="execution" value="e2s1" />
              <input type="hidden" name="cllt" value="userNameLogin" />
              <input type="hidden" id="pwdEncryptSalt" value="AbCdEfGh12345678" />
            </form>
        """.trimIndent()

        val form = CasAutoLogin.parseLoginForm(html)
        assertEquals("AbCdEfGh12345678", form.encryptSalt)
        assertEquals("e2s1", form.hiddenFields["execution"])
        assertEquals("userNameLogin", form.hiddenFields["cllt"])
    }

    @Test
    fun `newer root cookie beats stale longer path cookie while jw paths stay isolated`() {
        assertTrue(CampusCookieJar.pathsOverlap("/authserver", "/"))
        assertTrue(CampusCookieJar.pathsOverlap("/app", "/app/"))
        assertFalse(CampusCookieJar.pathsOverlap("/sso", "/jwglxt"))

        val now = 1_000_000L
        val cookies = listOf(
            CookieEntity(
                domain = "authserver.ecut.edu.cn",
                name = "route",
                value = "STALE_NODE",
                path = "/authserver",
                expiresAt = 0L,
                secure = true,
                updatedAt = now - 60_000L
            ),
            CookieEntity(
                domain = "authserver.ecut.edu.cn",
                name = "route",
                value = "FRESH_NODE",
                path = "/",
                expiresAt = 0L,
                secure = true,
                updatedAt = now
            )
        )
        val selected = CampusCookieJar.selectCookies(
            rows = cookies,
            host = "authserver.ecut.edu.cn",
            requestPath = "/authserver/login",
            now = now
        )
        assertEquals(1, selected.size)
        assertEquals("FRESH_NODE", selected.first().value)

        // 教务 /sso 与 /jwglxt 互不干扰
        val jwCookies = listOf(
            CookieEntity("172.20.130.13", "JSESSIONID", "SSO_ID", "/sso", 0L, false, now),
            CookieEntity("172.20.130.13", "JSESSIONID", "JW_ID", "/jwglxt", 0L, false, now - 1_000L)
        )
        val jwSelected = CampusCookieJar.selectCookies(
            rows = jwCookies,
            host = "172.20.130.13",
            requestPath = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html",
            now = now
        )
        assertEquals(1, jwSelected.size)
        assertEquals("JW_ID", jwSelected.first().value)
    }
}
