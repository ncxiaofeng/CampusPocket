package com.ecut.campuspocket.capture

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ecut.campuspocket.capture.ui.CaptureRoot
import com.ecut.campuspocket.capture.ui.CaptureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureStore.init(applicationContext)
        // 仅调试包开启 WebView CDP，方便脚本化读取 localStorage / 回调 URL。
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            CaptureTheme {
                CaptureRoot()
            }
        }
    }
}
