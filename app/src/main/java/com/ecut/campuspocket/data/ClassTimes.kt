package com.ecut.campuspocket.data

/**
 * 东华理工大学南昌校区（广兰 / 枫林）作息时间。
 *
 * 官方：第 1 节 08:00-08:45，第 2 节 08:55-09:40，依此类推至第 10 节 19:55-20:40。
 * 抚州校区部分节次晚 10 分钟，当前版本未区分校区。
 */
object ClassTimes {

    val periods: List<Pair<String, String>> = listOf(
        "08:00" to "08:45",
        "08:55" to "09:40",
        "10:00" to "10:45",
        "10:55" to "11:40",
        "14:00" to "14:45",
        "14:55" to "15:40",
        "16:00" to "16:45",
        "16:55" to "17:40",
        "19:00" to "19:45",
        "19:55" to "20:40"
    )

    fun startTime(period: Int): String? = periods.getOrNull(period - 1)?.first

    fun endTime(period: Int): String? = periods.getOrNull(period - 1)?.second

    /** 1-10 节为正常作息，11-12 节只作为兜底。 */
    const val regularPeriods: Int = 10
}
