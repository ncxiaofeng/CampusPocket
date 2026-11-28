package com.ecut.campuspocket.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 纯本地离线中文截图文字识别（ML Kit Text Recognition Chinese）。
 *
 * 所有图像处理均在本机完成，不上传任何图片或文字到云端。
 */
object OfflineOcrRecognizer {

    suspend fun recognizeFromUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build()
                )
                try {
                    val result = suspendCancellableCoroutine { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener { text ->
                                if (cont.isActive) cont.resume(text)
                            }
                            .addOnFailureListener { err ->
                                if (cont.isActive) cont.resumeWithException(err)
                            }
                    }
                    // 按视觉行（y 坐标分组、同行按 x 坐标排序）重组文本，表格截图识别更整齐
                    val allLines = result.textBlocks
                        .flatMap { it.lines }
                        .filter { it.text.isNotBlank() }
                    if (allLines.isEmpty()) {
                        result.text.trim()
                    } else {
                        val sorted = allLines.sortedWith(
                            compareBy<com.google.mlkit.vision.text.Text.Line>(
                                { it.boundingBox?.top ?: 0 },
                                { it.boundingBox?.left ?: 0 }
                            )
                        )
                        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                        for (line in sorted) {
                            val box = line.boundingBox
                            val top = box?.top ?: 0
                            val height = (box?.height() ?: 24).coerceAtLeast(12)
                            val targetRow = rows.lastOrNull()?.takeIf { row ->
                                val refTop = row.first().boundingBox?.top ?: 0
                                kotlin.math.abs(top - refTop) <= height * 0.6f
                            }
                            if (targetRow != null) {
                                targetRow.add(line)
                            } else {
                                rows.add(mutableListOf(line))
                            }
                        }
                        rows.joinToString("\n") { row ->
                            row.sortedBy { it.boundingBox?.left ?: 0 }
                                .joinToString(" ") { it.text.trim() }
                        }.trim()
                    }
                } finally {
                    recognizer.close()
                }
            }
        }
}
