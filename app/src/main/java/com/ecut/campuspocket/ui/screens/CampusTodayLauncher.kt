package com.ecut.campuspocket.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 打开今日校园（找不到就跳应用商店/网页）。 */
object CampusTodayLauncher {
    private const val PACKAGE = "com.wisedu.cpdaily"

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return

        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://app.mi.com/details?id=$PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(web) }.isSuccess) return

        Toast.makeText(context, "未安装今日校园", Toast.LENGTH_SHORT).show()
    }
}
