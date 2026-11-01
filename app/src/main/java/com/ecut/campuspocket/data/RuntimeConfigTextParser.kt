package com.ecut.campuspocket.data

import com.google.gson.JsonParser

/**
 * 解析高级配置的批量导入文本。
 *
 * 支持两种格式：
 * - 三行 key=value（local.properties 里复制出来的样子）
 * - JSON：{"electricity.operator.pwd":"...","ujing.hmac.key":"...","huilife.app.sign.salt":"..."}
 */
object RuntimeConfigTextParser {
    const val KEY_ELECTRICITY = "electricity.operator.pwd"
    const val KEY_UJING = "ujing.hmac.key"
    const val KEY_HUILIFE = "huilife.app.sign.salt"

    fun parse(text: String): Map<String, String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyMap()
        return if (trimmed.startsWith("{")) parseJson(trimmed) else parseLines(trimmed)
    }

    private fun parseLines(text: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val index = line.indexOf('=')
            if (index <= 0) return@forEach
            val key = canonical(line.substring(0, index).trim()).orEmpty()
            val value = line.substring(index + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) values[key] = value
        }
        return values
    }

    private fun parseJson(text: String): Map<String, String> = runCatching {
        val root = JsonParser.parseString(text).asJsonObject
        val values = LinkedHashMap<String, String>()
        root.entrySet().forEach { (rawKey, element) ->
            if (!element.isJsonPrimitive) return@forEach
            val key = canonical(rawKey).orEmpty()
            val value = runCatching { element.asString }.getOrDefault("").trim()
            if (key.isNotBlank() && value.isNotBlank()) values[key] = value
        }
        values
    }.getOrDefault(emptyMap())

    private fun canonical(key: String): String? = when (key.lowercase()) {
        KEY_ELECTRICITY, "e" -> KEY_ELECTRICITY
        KEY_UJING, "u" -> KEY_UJING
        KEY_HUILIFE, "h" -> KEY_HUILIFE
        else -> null
    }
}
