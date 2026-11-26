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

enum class HuiLifeDeviceKind {
    /** 直饮水 / 开水 / 热水机。 */
    WATER,

    /** 吹风机 / 电吹风。 */
    HAIR_DRYER,

    /** 淋浴 / 洗浴。 */
    SHOWER,

    /** 洗衣机 / 烘干机。 */
    LAUNDRY,

    /** 其它或暂未识别类型。 */
    OTHER
}

data class HuiLifeDevice(
    val id: String,
    val name: String,
    val detail: String,
    val typeCode: String = ""
) {
    val kind: HuiLifeDeviceKind
        get() = inferDeviceKind(name, detail, typeCode)

    companion object {
        fun inferDeviceKind(
            name: String,
            detail: String = "",
            typeCode: String = ""
        ): HuiLifeDeviceKind {
            // 优先匹配设备名称本身，避免被大区副标题里的“（饮水、开水、洗衣）”误导。
            val primary = name.trim()
            return when {
                primary.contains("吹风") || primary.contains("风筒") -> HuiLifeDeviceKind.HAIR_DRYER
                primary.contains("淋浴") || primary.contains("洗澡") ||
                    primary.contains("浴室") || primary.contains("洗浴") -> HuiLifeDeviceKind.SHOWER
                primary.contains("洗衣") || primary.contains("烘干") ||
                    primary.contains("洗鞋") -> HuiLifeDeviceKind.LAUNDRY
                primary.contains("饮水") || primary.contains("直饮") ||
                    primary.contains("开水") || primary.contains("热水") ||
                    primary.contains("水机") || primary.contains("净水") -> HuiLifeDeviceKind.WATER
                else -> when (typeCode.trim().lowercase()) {
                    "dryer", "hairdryer", "hair_dryer", "blower" -> HuiLifeDeviceKind.HAIR_DRYER
                    "shower", "bath" -> HuiLifeDeviceKind.SHOWER
                    "washer", "laundry" -> HuiLifeDeviceKind.LAUNDRY
                    "water", "drink" -> HuiLifeDeviceKind.WATER
                    else -> {
                        val sub = detail.trim()
                        when {
                            sub.contains("吹风") -> HuiLifeDeviceKind.HAIR_DRYER
                            sub.contains("淋浴") && !sub.contains("饮水") -> HuiLifeDeviceKind.SHOWER
                            else -> HuiLifeDeviceKind.OTHER
                        }
                    }
                }
            }
        }
    }
}

/** `/ui/app/dev/status` 的关键字段。 */
data class HuiLifeDeviceStatus(
    val deviceStatus: Int,
    val geneStatus: Int,
    val endTimeMillis: Long = 0L,
    val deviceName: String = "",
    val deviceDetail: String = "",
    val deviceTypeCode: String = ""
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
