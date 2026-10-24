package com.ecut.campuspocket.network

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 学校零信任客户端（aTrust / Sangfor）的唤起辅助。
 *
 * App 不做隧道协议本身：aTrust 使用深信服私有协议，必须由官方客户端 /
 * 官方 SDK 建立隧道。这里只负责发现、打开客户端，或引导安装。
 * 客户端连上后，CampusNetworkStatus 的内网探测会自然变成 OnCampus。
 */
object AtrustClient {

    private val candidatePackages = listOf(
        "com.sangfor.trust",
        "com.sangfor.atrust",
        "com.sangfor.vpn.client.phone",
        "com.sangfor.vpn",
        "com.sangfor.sdp",
        "com.sangfor.trustclient"
    )

    fun installedPackage(context: Context): String? {
        val manager = context.packageManager
        return candidatePackages.firstOrNull { packageName ->
            runCatching { manager.getPackageInfo(packageName, 0) }.isSuccess
        }
    }

    fun launch(context: Context): Boolean {
        val packageName = installedPackage(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 没有安装客户端时，先搜应用商店，再退到学校使用指南。 */
    fun openStoreOrGuide(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=aTrust"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return
        val guide = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://nic.ecut.edu.cn/31/9d/c4905a143773/page.htm")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(guide) }
    }
}
