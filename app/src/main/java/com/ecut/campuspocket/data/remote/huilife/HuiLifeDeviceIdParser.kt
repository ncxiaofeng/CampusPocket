package com.ecut.campuspocket.data.remote.huilife

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 把 798 设备二维码/手动输入收敛成设备编号或 qrId。
 *
 * 参考官方/第三方客户端的二维码格式：
 * - 纯数字 / 12-16 位小写字母数字：设备编号
 * - ilife798s://i/device/(nav|nfc)?id=...：设备编号
 * - /q/{n}/{kind}?id=... 或 /q/1/bm?/...：需要 /qr/use 换取设备编号
 * - 其他带 id/did 的 URL：直接当设备编号
 */
object HuiLifeDeviceIdParser {
    data class Parsed(
        val deviceId: String? = null,
        val qrId: String? = null
    )

    private val digits = Regex("""^\d+$""")
    private val deviceIdFormat = Regex("""^[0-9a-z]{12,16}$""")
    private val flexibleId = Regex("""^[A-Za-z0-9][A-Za-z0-9_-]{5,63}$""")
    private val appLink = Regex("""^ilife798s://i/device/(nav|nfc)\?id=[^&#]+""")
    private val qrUseLink = Regex("""^https?://.+?/q/\d+/(b|s|billing|vip|wallet)\?id=[^&#]+""")
    private val bmLink = Regex("""^https?://.+?/q/\d+/bm\?/[A-Za-z0-9-]+""")
    private val genericQrLink = Regex("""^https?://.+?/q/\d+/[A-Za-z0-9-]+""")
    private val keyPattern = Regex(
        """(?:[?&#;,\s{]|^)["']?(?:did|deviceId|device_id|id)["']?\s*(?:=|:)\s*["']?([^&#/,}\s"']+)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): Parsed {
        val input = decode(raw.trim())
        if (input.isBlank()) return Parsed()

        if (digits.matches(input) || deviceIdFormat.matches(input)) {
            return Parsed(deviceId = input)
        }
        if (appLink.matches(input)) {
            return Parsed(deviceId = queryValue(input, "id"))
        }
        if (qrUseLink.matches(input) || bmLink.matches(input)) {
            val id = queryValue(input, "id")
                .ifBlank { input.substringAfter("/bm?/", "").substringBefore('?') }
            return Parsed(qrId = id.takeIf { it.isNotBlank() })
        }
        if (genericQrLink.matches(input)) {
            val id = input.trimEnd('/').substringAfterLast('/').substringBefore('?')
            return Parsed(deviceId = id.takeIf { it.isNotBlank() })
        }

        val fromKey = keyPattern.find(input)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            ?.removePrefix("did:")
            ?.removePrefix("DID:")
        if (!fromKey.isNullOrBlank()) {
            return Parsed(deviceId = fromKey.take(160))
        }
        if (flexibleId.matches(input) && input.any(Char::isDigit)) {
            return Parsed(deviceId = input)
        }
        return Parsed()
    }

    fun normalize(raw: String): String {
        val parsed = parse(raw)
        return parsed.deviceId ?: parsed.qrId ?: ""
    }

    private fun queryValue(value: String, key: String): String {
        val uri = runCatching { URI(value) }.getOrNull()
        val rawQuery = uri?.rawQuery
            ?: value.substringAfter('?', "").substringBefore('#')
        rawQuery.split('&').forEach { pair ->
            val index = pair.indexOf('=')
            if (index > 0 && pair.substring(0, index).equals(key, ignoreCase = true)) {
                return decode(pair.substring(index + 1))
            }
        }
        return ""
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
