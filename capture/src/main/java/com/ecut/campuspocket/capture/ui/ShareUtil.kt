package com.ecut.campuspocket.capture.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ecut.campuspocket.capture.CaptureStore
import java.io.File

fun shareCaptureFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享抓包日志"))
}

fun exportAndShare(context: Context) {
    val file = CaptureStore.export(context)
    if (file == null) {
        Toast.makeText(context, "还没有可导出的日志", Toast.LENGTH_SHORT).show()
        return
    }
    shareCaptureFile(context, file)
}
