package com.ecut.campuspocket

import android.app.Application
import android.webkit.WebView

/**
 * 进程级入口。
 *
 * 数据库与凭据容器采用懒加载，避免冷启动时初始化 SQLCipher。
 */
class CampusPocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        if (BuildConfig.DEBUG) {
            // 调试包允许通过 chrome://inspect / adb forward 观察内嵌网页。
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
