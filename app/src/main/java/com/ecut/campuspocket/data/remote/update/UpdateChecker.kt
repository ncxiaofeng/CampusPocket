package com.ecut.campuspocket.data.remote.update

import com.ecut.campuspocket.data.AppLinks
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class AppUpdateInfo(
    /** 归一化后的版本号，例如 0.4.1。 */
    val version: String,
    /** 原始 tag，例如 v0.4.1。 */
    val tagName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    /** GitHub Release 里的 APK 附件；没有时回退到 Release 页。 */
    val apkDownloadUrl: String?
)

/**
 * 通过 GitHub Releases API 检查更新。
 *
 * 仓库公开后无需任何 token；仓库私有或网络不可达时返回失败，
 * 由界面引导用户走蓝奏云镜像。
 */
class UpdateChecker(
    private val client: OkHttpClient
) {

    suspend fun check(currentVersion: String): Result<AppUpdateInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JsonParser
                    .parseString(fetchLatestRelease())
                    .asJsonObject
                val tagName = root.string("tag_name")
                val latestVersion = normalizeVersion(tagName)
                if (latestVersion.isBlank() || !isNewer(latestVersion, currentVersion)) {
                    null
                } else {
                    AppUpdateInfo(
                        version = latestVersion,
                        tagName = tagName,
                        releaseNotes = root.string("body"),
                        releasePageUrl = root.string("html_url")
                            .ifBlank { AppLinks.GITHUB_RELEASES },
                        apkDownloadUrl = apkAssetUrl(root)
                    )
                }
            }
        }

    private fun fetchLatestRelease(): String {
        val request = Request.Builder()
            .url(AppLinks.GITHUB_LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CampusPocket")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(
                    if (response.code == 404) {
                        "更新仓库暂不可用（404）"
                    } else {
                        "更新接口返回 HTTP ${response.code}"
                    }
                )
            }
            return body
        }
    }

    private fun apkAssetUrl(root: JsonObject): String? {
        val assets = root.getAsJsonArray("assets") ?: return null
        return assets.mapNotNull { element ->
            val item = element.asJsonObject
            val name = item.string("name")
            val url = item.string("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) url else null
        }.firstOrNull()
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    companion object {

        fun normalizeVersion(raw: String): String = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()

        /**
         * 语义化版本比较：1.0.10 > 1.0.9，1.0 == 1.0.0。
         * 非数字后缀（-beta）在 normalizeVersion 里已去掉。
         */
        fun isNewer(remote: String, local: String): Boolean {
            val remoteParts = parseVersion(remote)
            val localParts = parseVersion(local)
            if (remoteParts.isEmpty() || localParts.isEmpty()) return false
            val size = maxOf(remoteParts.size, localParts.size)
            for (index in 0 until size) {
                val remoteValue = remoteParts.getOrElse(index) { 0 }
                val localValue = localParts.getOrElse(index) { 0 }
                if (remoteValue != localValue) return remoteValue > localValue
            }
            return false
        }

        private fun parseVersion(raw: String): List<Int> =
            normalizeVersion(raw).split('.').mapNotNull { it.toIntOrNull() }
    }
}
