package com.ecut.campuspocket.network.signing

import java.security.MessageDigest
import java.util.UUID

/**
 * 慧生活798 的 X-Ashe / X-Nami 签名。
 *
 * X-Ashe = MD5( lower(MD5("servername.methodname")) + timestamp + randomStr )
 * X-Nami 未登录时取 UUID（去横线）前 17 位；登录后的生成规则待抓包确认。
 *
 * 目前 servername / methodname / timestamp 的传输位置尚未校准，
 * 因此签名器独立存在，不默认挂到任何客户端上。
 */
object AsheSigner {

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sign(
        servername: String,
        methodname: String,
        timestamp: String,
        randomStr: String
    ): String {
        val inner = md5Hex("$servername.$methodname").lowercase()
        return md5Hex(inner + timestamp + randomStr)
    }

    fun anonymousNami(): String =
        UUID.randomUUID().toString().replace("-", "").take(17)

    /**
     * 登录后的 X-Nami 规则未知，先用用户标识的 MD5 前 17 位占位。
     */
    fun userNami(userKey: String): String =
        md5Hex(userKey).take(17)
}
