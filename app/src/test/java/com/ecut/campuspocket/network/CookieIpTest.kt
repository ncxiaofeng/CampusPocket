package com.ecut.campuspocket.network

import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Test

class CookieIpTest {

    @Test
    fun `okhttp accepts an ip address as cookie domain`() {
        val cookie = Cookie.Builder()
            .name("JSESSIONID")
            .value("abc123")
            .domain("172.20.130.13")
            .path("/")
            .build()
        assertEquals("172.20.130.13", cookie.domain)
    }
}
