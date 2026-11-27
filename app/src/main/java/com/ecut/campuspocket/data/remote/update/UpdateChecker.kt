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
                val latestRoot = JsonParser
                    .parseString(fetchJson(AppLinks.GITHUB_LATEST_RELEASE_API))
                    .asJsonObject
                val candidate = if (isEligibleAndroidRelease(latestRoot)) {
                    latestRoot
                } else {
                    // 当仓库同时发布 iOS（ios-v*）等其他平台 tag 并抢占 Latest 时，
                    // 自动从最近 Release 列表里筛选真正的最新 Android 正式版。
                    selectLatestAndroidRelease(fetchJson(AppLinks.GITHUB_RELEASES_LIST_API))
                } ?: return@runCatching null

                val tagName = candidate.string("tag_name")
                val latestVersion = normalizeVersion(tagName)
                if (latestVersion.isBlank() || !isNewer(latestVersion, currentVersion)) {
                    null
                } else {
                    AppUpdateInfo(
                        version = latestVersion,
                        tagName = tagName,
                        releaseNotes = candidate.string("body"),
                        releasePageUrl = candidate.string("html_url")
                            .ifBlank { AppLinks.GITHUB_RELEASES },
                        apkDownloadUrl = apkAssetUrl(candidate)
                    )
                }
            }
        }

    private fun fetchJson(url: String): String {
        val request = Request.Builder()
            .url(url)
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

        private val ANDROID_TAG_REGEX = Regex("^[vV]?\\d+(?:\\.\\d+)+(?:[-+].+)?$")

        fun isAndroidReleaseTag(tagName: String): Boolean =
            ANDROID_TAG_REGEX.matches(tagName.trim())

        internal fun isEligibleAndroidRelease(root: JsonObject): Boolean {
            val draft = root.get("draft")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            val prerelease = root.get("prerelease")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            if (draft || prerelease) return false
            val tag = root.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            return isAndroidReleaseTag(tag)
        }

        internal fun selectLatestAndroidRelease(releasesJson: String): JsonObject? {
            val array = runCatching { JsonParser.parseString(releasesJson).asJsonArray }.getOrNull()
                ?: return null
            var best: JsonObject? = null
            var bestVersion = ""
            for (el in array) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                if (!isEligibleAndroidRelease(obj)) continue
                val tag = obj.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val version = normalizeVersion(tag)
                if (version.isBlank()) continue
                if (best == null || isNewer(version, bestVersion)) {
                    best = obj
                    bestVersion = version
                }
            }
            return best
        }

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
