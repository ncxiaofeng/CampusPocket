package com.ecut.campuspocket.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class SessionExpiredException(
    message: String = "登录状态已过期，请重新登录"
) : IOException(message)

/**
 * 校外且还没有零信任 WebVPN 代理会话时抛出。
 * 需要用户先在门户里打开一次“新教务管理系统”，让代理域拿到会话 Cookie。
 */
class ProxySessionRequiredException(
    message: String = "零信任代理会话已失效：请在「更多 → 零信任 VPN → 零信任门户」里打开一次“新教务管理系统”，再回来同步。"
) : IOException(message)

/**
 * CAS 会话过期通常表现为 401，或被重定向回登录页。
 * 只挂在需要统一认证的客户端上，避免影响普通 H5 接口。
 */
class SessionExpiredInterceptor(
    private val loginMarkers: List<String> = listOf(
        "authserver",
        "cas/login",
        "passport/v1/auth",
        "login_slogin",
        "jziotlogin",
        "atrust1.ecut.edu.cn/portal"
    )
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            response.close()
            throw SessionExpiredException()
        }
        if (response.isRedirect) {
            val location = response.header("Location").orEmpty()
            if (loginMarkers.any { marker -> location.contains(marker, ignoreCase = true) }) {
                response.close()
                throw SessionExpiredException()
            }
        }
        return response
    }
}
