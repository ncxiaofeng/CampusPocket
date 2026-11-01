package com.ecut.campuspocket.data.remote.huilife

/** 慧生活接口诊断；只记录 code/msg/字段名，不记录 token。 */
object HuiLifeDiagnostics {
    @Volatile
    var lastHttp: String = "未请求"

    @Volatile
    var lastResponse: String = "未请求"

    @Volatile
    var lastMaster: String = "未请求"

    @Volatile
    var lastWallet: String = "未请求"

    @Volatile
    var lastBills: String = "未请求"

    @Volatile
    var lastDeviceStatus: String = "未请求"

    fun summary(extra: String = ""): String = buildString {
        append("HTTP=").append(lastHttp)
        append("; 响应=").append(lastResponse)
        append("; master=").append(lastMaster)
        append("; wallet=").append(lastWallet)
        append("; bills=").append(lastBills)
        append("; status=").append(lastDeviceStatus)
        if (extra.isNotBlank()) append("; ").append(extra)
    }
}
