package com.ecut.campuspocket.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

enum class RemoteClaimMode {
    /** 单人阅后即焚：拉取保存后立刻向云端发 DELETE 销毁。 */
    ONCE,

    /** 群体限时有效：到期后由云端预签名自动过期。 */
    GROUP
}

data class RemoteConfigClaim(
    val getUrl: String,
    val deleteUrl: String?,
    val mode: RemoteClaimMode,
    val expiresAtEpochSec: Long = 0L
)

data class RemoteClaimOutcome(
    val values: Map<String, String>,
    val mode: RemoteClaimMode,
    val burned: Boolean
)

/**
 * 解析一次性 / 限时远程配置二维码载荷。
 *
 * 安全护栏：
 * - 仅接受 `https://` 且域名属于腾讯云 COS（`*.myqcloud.com`）与 `/claim/` 前缀；
 * - 禁止指向任意第三方站点或内网地址，避免恶意二维码 SSRF。
 */
object RemoteConfigClaimParser {

    fun parse(raw: String): RemoteConfigClaim? {
        val text = raw.trim()
        if (!text.startsWith("{")) return null
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: return null
        val version = obj.get("cp_claim")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
        if (version != 1) return null

        val getUrl = obj.get("g")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        if (!isAllowedClaimUrl(getUrl)) return null

        val modeText = obj.get("m")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        val mode = if (modeText.equals("group", ignoreCase = true)) {
            RemoteClaimMode.GROUP
        } else {
            RemoteClaimMode.ONCE
        }
        val exp = obj.get("exp")?.takeIf { it.isJsonPrimitive }?.let {
            runCatching { it.asLong }.getOrDefault(0L)
        } ?: 0L

        val explicitDeleteUrl = obj.get("d")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        val deleteSig = obj.get("ds")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        val deleteUrl = when {
            mode != RemoteClaimMode.ONCE -> null
            explicitDeleteUrl.isNotBlank() && isAllowedClaimUrl(explicitDeleteUrl) -> explicitDeleteUrl
            deleteSig.matches(Regex("^[0-9a-fA-F]{40}$")) -> deriveDeleteUrl(getUrl, deleteSig)
            else -> null
        }

        return RemoteConfigClaim(
            getUrl = getUrl,
            deleteUrl = deleteUrl,
            mode = mode,
            expiresAtEpochSec = exp
        )
    }

    internal fun isAllowedClaimUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        val host = parsed.host.lowercase()
        if (!host.endsWith(".myqcloud.com") || host.length <= ".myqcloud.com".length) return false
        if (!parsed.encodedPath.startsWith("/claim/")) return false
        return parsed.queryParameter("q-signature")?.isNotBlank() == true
    }

    internal fun deriveDeleteUrl(getUrl: String, deleteSignature: String): String? {
        val parsed = getUrl.toHttpUrlOrNull() ?: return null
        if (parsed.queryParameter("q-signature").isNullOrBlank()) return null
        return parsed.newBuilder()
            .setQueryParameter("q-signature", deleteSignature)
            .build()
            .toString()
    }
}

/**
 * 执行一次性 / 限时配置码的云端拉取与阅后即焚销毁。
 */
class RemoteConfigClaimClient(
    private val client: OkHttpClient
) {

    suspend fun claim(claim: RemoteConfigClaim): Result<RemoteClaimOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val getRequest = Request.Builder()
                    .url(claim.getUrl)
                    .get()
                    .build()
                val body = client.newCall(getRequest).execute().use { response ->
                    when (response.code) {
                        404 -> throw IOException("该配置码已被领取或已失效")
                        403 -> throw IOException("该配置码已过期或无访问权限，请向分享者重新获取")
                        in 200..299 -> {
                            val bytes = response.body?.bytes()
                                ?: throw IOException("配置码返回内容为空")
                            if (bytes.size > MAX_BODY_BYTES) {
                                throw IOException("配置码内容异常（体积超限）")
                            }
                            String(bytes, Charsets.UTF_8)
                        }
                        else -> throw IOException("配置码拉取失败（HTTP ${response.code}）")
                    }
                }
                val values = RuntimeConfigTextParser.parse(body)
                if (values.isEmpty()) {
                    throw IOException("配置码内没有识别到有效参数")
                }
                var burned = false
                if (claim.mode == RemoteClaimMode.ONCE && !claim.deleteUrl.isNullOrBlank()) {
                    burned = runCatching {
                        val delRequest = Request.Builder()
                            .url(claim.deleteUrl)
                            .delete()
                            .build()
                        client.newCall(delRequest).execute().use { resp ->
                            resp.code in 200..299 || resp.code == 404
                        }
                    }.getOrDefault(false)
                }
                RemoteClaimOutcome(
                    values = values,
                    mode = claim.mode,
                    burned = burned
                )
            }
        }

    companion object {
        private const val MAX_BODY_BYTES = 4096
    }
}
