package com.ecut.campuspocket

import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingCourse
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingHtmlParser
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingTerm
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingUserAgent
import com.ecut.campuspocket.network.CasAutoLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChaoxingHtmlParserTest {

    private val course = ChaoxingCourse(
        courseId = "100",
        classId = "200",
        name = "马克思主义基本原理",
        className = "1班",
        teacher = "张老师"
    )

    @Test
    fun `parses homework rows`() {
        val html = """
            <ul>
              <dt data="/work/phone/task-work?id=1&amp;courseId=100&amp;classId=200" status="0">
                <h3>第一次作业</h3>
                <p>截止时间：2026-11-10 23:59</p>
                <b>做作业</b>
              </dt>
            </ul>
        """.trimIndent()
        val tasks = ChaoxingHtmlParser.parseWorkList(html, course)
        assertEquals(1, tasks.size)
        val task = tasks.first()
        assertEquals("第一次作业", task.title)
        assertEquals("做作业", task.statusText)
        assertEquals("截止时间：2026-11-10 23:59", task.detail)
        assertTrue(task.url.contains("mooc1-api.chaoxing.com/work/phone/task-work"))
        assertTrue(task.url.contains("courseId=100"))
        assertTrue(task.isPending)
    }

    @Test
    fun `reads status from span when bold only says view`() {
        val html = """
            <dt data="/work/phone/task-work?id=2" status="1">
              <h3>第二次作业</h3>
              <p>截止时间：2026-11-12</p>
              <span class="status">待批阅</span>
              <b>查看</b>
            </dt>
        """.trimIndent()
        val task = ChaoxingHtmlParser.parseWorkList(html, course).first()
        assertEquals("待批阅", task.statusText)
        assertEquals("待批阅", task.statusLabel)
        assertTrue(!task.needsAction)
        assertTrue(task.isDone)
    }

    @Test
    fun `falls back to li work list structure`() {
        val html = """
            <ul class="nav">
              <li data="/work/phone/task-work?id=3" status="0">
                <div role="option">
                  <p>第三次作业</p>
                  <span class="status">待完成</span>
                  <span>马克思主义基本原理</span>
                </div>
              </li>
            </ul>
        """.trimIndent()
        val task = ChaoxingHtmlParser.parseWorkList(html, course).first()
        assertEquals("第三次作业", task.title)
        assertEquals("待完成", task.statusText)
        assertTrue(task.needsAction)
    }

    @Test
    fun `falls back to li exam structure`() {
        val html = """
            <ul class="ks_list">
              <li data="/exam/phone/task-exam?id=4" status="0">
                <dl><dt>期末考试</dt><dd>12月20日 09:00</dd></dl>
                <span class="ks_state">未开始</span>
              </li>
            </ul>
        """.trimIndent()
        val task = ChaoxingHtmlParser.parseExamList(html, course).first()
        assertEquals("期末考试", task.title)
        assertEquals("未开始", task.statusText)
        assertTrue(task.needsAction)
    }

    @Test
    fun `ranks current semester before previous`() {
        val current = ChaoxingTerm.rank("2026-2027学年 第一学期", LocalDate.of(2026, 11, 1))
        val previous = ChaoxingTerm.rank("2025-2026-2", LocalDate.of(2026, 11, 1))
        assertEquals(ChaoxingTerm.CURRENT, current)
        assertEquals(ChaoxingTerm.PREVIOUS, previous)
        assertEquals("本学期", ChaoxingTerm.label(current))
    }

    @Test
    fun `parses exam rows and skips header`() {
        val html = """
            <table>
              <tr><td>考试名称</td><td>分数</td><td>状态</td></tr>
              <tr data="/exam/phone/task-exam?id=9" title="期中考试" status="0">
                <td>期中考试</td><td>100 分</td><td>未开始</td>
              </tr>
            </table>
        """.trimIndent()
        val tasks = ChaoxingHtmlParser.parseExamList(html, course)
        assertEquals(1, tasks.size)
        assertEquals("期中考试", tasks.first().title)
        assertEquals("100 分", tasks.first().detail)
        assertEquals("未开始", tasks.first().statusText)
    }

    @Test
    fun `official UA contains signed suffix`() {
        val ua = ChaoxingUserAgent.build("0123456789abcdef0123456789abcdef")
        assertTrue(ua.contains("(schild:"))
        assertTrue(ua.contains("com.chaoxing.mobile/ChaoXingStudy"))
        assertTrue(ua.endsWith("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `HuiLife app login signature uses the device salt`() {
        val sign = com.ecut.campuspocket.data.remote.huilife.HuiLifeClient.signAppLogin(
            "13800138000",
            "123456",
            "test_salt_1234567890"
        )
        assertEquals("332420bfa8f97555aebfe376661f2a15", sign)
    }

    @Test
    fun `CAS password encryption keeps prefix and password`() {
        val salt = "QYeYdDbISzxf7hnr"
        val iv = "ABCDEFGHJKMNPQRS"
        val encrypted = CasAutoLogin.encryptCasPassword("hunter2!", salt, iv)
        val decoded = java.util.Base64.getDecoder().decode(encrypted)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        )
        val plain = String(cipher.doFinal(decoded), Charsets.UTF_8)
        assertEquals(64 + "hunter2!".length, plain.length)
        assertTrue(plain.endsWith("hunter2!"))
    }
}
