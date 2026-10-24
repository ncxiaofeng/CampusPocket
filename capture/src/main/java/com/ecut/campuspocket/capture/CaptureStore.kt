package com.ecut.campuspocket.capture

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class CaptureKind {
    REQUEST, RESPONSE, ERROR, PAGE, CONSOLE, WS
}

data class CaptureEntry(
    val entryId: Long,
    val correlationId: String,
    val kind: CaptureKind,
    val timestamp: Long,
    val method: String,
    val url: String,
    val status: Int?,
    val mime: String,
    val headers: String,
    val body: String,
    val pageUrl: String,
    val error: String
) {
    val host: String
        get() = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
}

/**
 * 抓包日志仓库：内存最多保留 800 条，同时逐行追加到应用私有 JSONL 文件。
 * JS 注入脚本已对 Cookie / Authorization / 密码 / 验证码等字段做脱敏。
 */
object CaptureStore {

    private const val MAX_ENTRIES = 800
    private const val MAX_URL = 4_000
    private const val MAX_HEADERS = 20_000
    private const val MAX_BODY = 120_000

    private val _entries = MutableStateFlow<List<CaptureEntry>>(emptyList())
    val entries: StateFlow<List<CaptureEntry>> = _entries.asStateFlow()

    private val counter = AtomicLong(0)
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Volatile
    private var logDir: File? = null

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        synchronized(lock) {
            if (logFile != null) return
            val dir = File(context.filesDir, "capture_logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            logDir = dir
            logFile = File(dir, "capture_$stamp.jsonl").apply { createNewFile() }
        }
    }

    fun addRaw(payload: String) {
        if (payload.length > 1_000_000) return
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val entry = fromJson(json) ?: return
        appendEntry(entry, json)
    }

    fun addPage(url: String) {
        val entry = CaptureEntry(
            entryId = counter.incrementAndGet(),
            correlationId = "",
            kind = CaptureKind.PAGE,
            timestamp = System.currentTimeMillis(),
            method = "GET",
            url = url.take(MAX_URL),
            status = null,
            mime = "",
            headers = "",
            body = "",
            pageUrl = url.take(MAX_URL),
            error = ""
        )
        appendEntry(entry, toJson(entry))
    }

    fun addPageError(url: String, message: String) {
        val entry = CaptureEntry(
            entryId = counter.incrementAndGet(),
            correlationId = "",
            kind = CaptureKind.ERROR,
            timestamp = System.currentTimeMillis(),
            method = "",
            url = url.take(MAX_URL),
            status = null,
            mime = "",
            headers = "",
            body = "",
            pageUrl = url.take(MAX_URL),
            error = message.take(4_000)
        )
        appendEntry(entry, toJson(entry))
    }

    fun addConsole(level: String, message: String) {
        val entry = CaptureEntry(
            entryId = counter.incrementAndGet(),
            correlationId = "",
            kind = CaptureKind.CONSOLE,
            timestamp = System.currentTimeMillis(),
            method = level.uppercase(Locale.US),
            url = "",
            status = null,
            mime = "",
            headers = "",
            body = message.take(MAX_BODY),
            pageUrl = "",
            error = ""
        )
        appendEntry(entry, toJson(entry))
    }

    fun clear() {
        _entries.value = emptyList()
        io.execute {
            synchronized(lock) {
                runCatching { logFile?.writeText("") }
            }
        }
    }

    fun currentFile(): File? = logFile

    fun listLogFiles(): List<File> {
        return logDir?.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun export(context: Context): File? {
        val source = logFile ?: return null
        runCatching { io.submit {}.get(2, TimeUnit.SECONDS) }
        if (source.length() == 0L) return null
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val target = File(dir, "capture_$stamp.jsonl")
        return runCatching {
            source.copyTo(target, overwrite = true)
            target
        }.getOrNull()
    }

    private fun fromJson(json: JSONObject): CaptureEntry? {
        val kind = when (json.optString("t")) {
            "req" -> CaptureKind.REQUEST
            "res" -> CaptureKind.RESPONSE
            "err" -> CaptureKind.ERROR
            "page" -> CaptureKind.PAGE
            "console" -> CaptureKind.CONSOLE
            "ws" -> CaptureKind.WS
            else -> return null
        }
        val headers = json.optJSONObject("headers")?.toString()
            ?: json.optString("headers").take(MAX_HEADERS)
        return CaptureEntry(
            entryId = counter.incrementAndGet(),
            correlationId = json.optString("id"),
            kind = kind,
            timestamp = json.optLong("ts", System.currentTimeMillis()),
            method = json.optString("method").take(16),
            url = json.optString("url").take(MAX_URL),
            status = if (json.has("status")) json.optInt("status") else null,
            mime = json.optString("mime").take(200),
            headers = headers.take(MAX_HEADERS),
            body = json.optString("body").take(MAX_BODY),
            pageUrl = json.optString("page").take(MAX_URL),
            error = json.optString("error").take(4_000)
        )
    }

    private fun toJson(entry: CaptureEntry): JSONObject {
        return JSONObject().apply {
            put("t", when (entry.kind) {
                CaptureKind.REQUEST -> "req"
                CaptureKind.RESPONSE -> "res"
                CaptureKind.ERROR -> "err"
                CaptureKind.PAGE -> "page"
                CaptureKind.CONSOLE -> "console"
                CaptureKind.WS -> "ws"
            })
            put("id", entry.correlationId)
            put("ts", entry.timestamp)
            put("method", entry.method)
            put("url", entry.url)
            entry.status?.let { put("status", it) }
            put("mime", entry.mime)
            put("body", entry.body)
            put("page", entry.pageUrl)
            put("error", entry.error)
        }
    }

    private fun appendEntry(entry: CaptureEntry, json: JSONObject) {
        _entries.update { current -> (current + entry).takeLast(MAX_ENTRIES) }
        val line = json.toString()
        io.execute {
            synchronized(lock) {
                runCatching { logFile?.appendText(line + "\n") }
            }
        }
    }
}
