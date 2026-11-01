package com.ecut.campuspocket.data.remote.chaoxing

data class ChaoxingCourse(
    val courseId: String,
    val classId: String,
    val name: String,
    val className: String,
    val teacher: String,
    /** 0=本学期，1=上一学年，2=下一学年，3=更早/更晚，4=无法判断。 */
    val termRank: Int = ChaoxingTerm.UNKNOWN
) {
    val termLabel: String
        get() = ChaoxingTerm.label(termRank)
}

data class ChaoxingTask(
    val type: String,
    val title: String,
    val courseName: String,
    val courseId: String,
    val classId: String,
    val url: String,
    val statusText: String,
    val detail: String,
    val status: Int,
    val termRank: Int = ChaoxingTerm.UNKNOWN
) {
    val key: String
        get() = "$type|$courseId|$classId|$title|$url"

    /** 服务端状态优先；把“做作业/去完成”这类按钮文案归一成“待完成”。 */
    val statusLabel: String
        get() {
            val raw = statusText.ifBlank {
                if (status == 0) "待完成" else "已结束"
            }
            return when {
                raw.contains("做作业") || raw == "去完成" -> "待完成"
                raw == "查看" -> "已完成"
                else -> raw
            }
        }

    /** 需要学生动手：待完成、未提交、去完成等；“待批阅”不算。 */
    val needsAction: Boolean
        get() {
            val text = statusText
            if (text.isBlank()) return status == 0
            return STATUS_ACTION.any { text.contains(it) }
        }

    /** 已完成、已提交、待批阅、已批阅、已过期等都算“无需再动手”。 */
    val isDone: Boolean
        get() {
            val text = statusText
            return STATUS_DONE.any { text.contains(it) }
        }

    /** 兼容旧调用；通知和排序请优先用 [needsAction]。 */
    val isPending: Boolean
        get() = needsAction

    val termLabel: String
        get() = ChaoxingTerm.label(termRank)

    companion object {
        private val STATUS_ACTION = listOf(
            "待完成", "未完成", "未提交", "待提交", "做作业", "去完成",
            "未开始", "进行中", "待参加", "待考试"
        )
        private val STATUS_DONE = listOf(
            "已完成", "已提交", "待批阅", "已批阅", "已过期", "已结束", "已评分", "查看"
        )
    }
}

data class ChaoxingSign(
    val activeId: String,
    val title: String,
    val courseName: String,
    val courseId: String,
    val classId: String,
    val startTime: Long,
    val status: Int,
    val activeType: Int,
    val url: String,
    val termRank: Int = ChaoxingTerm.UNKNOWN
) {
    val key: String
        get() = activeId.ifBlank { "$courseId|$classId|$title|$startTime" }

    val isOngoing: Boolean
        get() = status == 1

    val termLabel: String
        get() = ChaoxingTerm.label(termRank)
}

data class ChaoxingSnapshot(
    val courses: List<ChaoxingCourse>,
    val works: List<ChaoxingTask>,
    val exams: List<ChaoxingTask>,
    val signs: List<ChaoxingSign>,
    val fetchedAt: Long,
    val warnings: List<String> = emptyList()
) {
    val pendingWorks: List<ChaoxingTask>
        get() = works.filter { it.needsAction }

    val pendingExams: List<ChaoxingTask>
        get() = exams.filter { it.needsAction }
}
