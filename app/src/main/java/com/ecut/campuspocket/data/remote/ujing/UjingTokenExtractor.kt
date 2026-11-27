package com.ecut.campuspocket.data.remote.ujing

import com.google.gson.JsonParser

/**
 * 从 U净 官方 H5 的 `evaluateJavascript` 返回值里安全提取并校验 JWT。
 *
 * 只提取登录完成后的个人会话令牌，不接触任何签名密钥。
 */
object UjingTokenExtractor {

    private val jwtRegex = Regex("""^[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}$""")

    /**
     * 解析 `WebView.evaluateJavascript` 返回的 JSON 字符串，若包含合法 JWT 则返回纯 token，否则返回 null。
     */
    fun extractJwtFromJsResult(rawJsResult: String?): String? {
        if (rawJsResult.isNullOrBlank()) return null
        val trimmed = rawJsResult.trim()
        if (trimmed == "null" || trimmed == "\"\"" || trimmed == "\"null\"" || trimmed == "\"undefined\"") {
            return null
        }
        val unquoted = runCatching {
            val element = JsonParser.parseString(trimmed)
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString
            } else {
                null
            }
        }.getOrNull() ?: trimmed.removeSurrounding("\"")
        return sanitizeJwt(unquoted)
    }

    /**
     * 规范化并校验 JWT 结构（header.payload.signature 三段 base64url）。
     */
    fun sanitizeJwt(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val cleaned = candidate.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
        if (cleaned.length < 30 || cleaned.length > 4096) return null
        return cleaned.takeIf { jwtRegex.matches(it) }
    }
}
