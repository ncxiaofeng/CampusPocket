package com.ecut.campuspocket.data

import java.net.URI
import java.net.URLDecoder

/**
 * 从今日校园/学校缴费入口链接里提取 operatorPwd。
 *
 * 只解析用户自己复制到剪贴板的官方链接；不联网、不猜测。
 */
object ElectricityLinkParser {
    private val urlPattern = Regex("""https?://[^\s]+""")

    fun extractOperatorPwd(raw: String): String? {
        val url = urlPattern.find(raw)?.value ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val params = LinkedHashMap<String, String>()
        parseQuery(uri.rawQuery, params)
        parseQuery(uri.rawFragment?.substringAfter('?', ""), params)
        return params.entries
            .firstOrNull { it.key.equals("operatorPwd", ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseQuery(raw: String?, output: MutableMap<String, String>) {
        raw.orEmpty().split('&').forEach { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@forEach
            val key = pair.substring(0, index).trim()
            val value = runCatching {
                URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            }.getOrDefault(pair.substring(index + 1))
            if (key.isNotBlank()) output[key] = value
        }
    }
}
