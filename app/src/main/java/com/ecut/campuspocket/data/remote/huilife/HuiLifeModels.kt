package com.ecut.campuspocket.data.remote.huilife

enum class HuiLifePlatform {
    /** 查询端：余额、设备、账单（ApplicationType=1,5）。 */
    ACCOUNT,

    /** 设备控制端：一键启动/停止（ApplicationType=1,1）。 */
    APP
}

data class HuiLifeSession(
    val token: String,
    val uid: String,
    val eid: String
)

data class HuiLifeDevice(
    val id: String,
    val name: String,
    val detail: String
)

/** `/ui/app/dev/status` 的关键字段。 */
data class HuiLifeDeviceStatus(
    val deviceStatus: Int,
    val geneStatus: Int,
    val endTimeMillis: Long = 0L
) {
    val label: String
        get() = when {
            deviceStatus == 0 -> "离线"
            geneStatus == 98 -> "禁用"
            geneStatus == 99 -> "在线"
            geneStatus == 1 || geneStatus == 30 -> "启动中"
            else -> "使用中"
        }

    val canStart: Boolean
        get() = deviceStatus != 0 && geneStatus == 99
}

data class HuiLifeWallet(
    val name: String,
    val balance: Double
)

data class HuiLifeBill(
    val id: String,
    val title: String,
    val amount: Double,
    val time: Long,
    val deviceId: String = "",
    val deviceName: String = ""
)
