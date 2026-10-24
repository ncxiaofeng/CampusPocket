package com.ecut.campuspocket.network.signing

import okhttp3.Interceptor
import okhttp3.Response
import kotlin.random.Random

/**
 * 给慧生活798 请求加签名头。
 *
 * 只添加已知的 X-Ashe / X-Nami；timestamp 与 randomStr 的传输位置
 * 需要真机抓包确认后再按实际接口调整。
 */
class AsheInterceptor(
    private val servername: String,
    private val methodNameProvider: (okhttp3.Request) -> String,
    private val userKeyProvider: () -> String? = { null },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val randomProvider: () -> String = { Random.nextInt(100000, 1000000).toString() }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val methodName = methodNameProvider(request)
        val timestamp = clock().toString()
        val randomStr = randomProvider()
        val ashe = AsheSigner.sign(servername, methodName, timestamp, randomStr)
        val userKey = userKeyProvider()
        val nami = if (userKey.isNullOrBlank()) {
            AsheSigner.anonymousNami()
        } else {
            AsheSigner.userNami(userKey)
        }

        val signed = request.newBuilder()
            .header("X-Ashe", ashe)
            .header("X-Nami", nami)
            .build()
        return chain.proceed(signed)
    }
}
