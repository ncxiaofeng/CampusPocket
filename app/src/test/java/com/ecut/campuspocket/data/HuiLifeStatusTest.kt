package com.ecut.campuspocket.data

import com.ecut.campuspocket.data.remote.huilife.HuiLifeClient
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDevice
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceIdParser
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceKind
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceStatus
import com.ecut.campuspocket.ui.screens.HuiLifeViewModel
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuiLifeStatusTest {

    @Test
    fun mapsGeneStatus() {
        assertEquals("离线", HuiLifeDeviceStatus(0, 99).label)
        assertEquals("在线", HuiLifeDeviceStatus(1, 99).label)
        assertEquals("禁用", HuiLifeDeviceStatus(1, 98).label)
        assertEquals("启动中", HuiLifeDeviceStatus(1, 1).label)
        assertEquals("使用中", HuiLifeDeviceStatus(1, 5).label)
    }

    @Test
    fun onlyOnlineCanStart() {
        assertTrue(HuiLifeDeviceStatus(1, 99).canStart)
        assertFalse(HuiLifeDeviceStatus(1, 5).canStart)
        assertFalse(HuiLifeDeviceStatus(0, 99).canStart)
    }

    @Test
    fun parsesQrUseIds() {
        val parsed = HuiLifeDeviceIdParser.parse(
            "https://cloud.hnkzy.com/q/1/b?id=abc12345"
        )
        assertEquals("abc12345", parsed.qrId)
        assertNull(parsed.deviceId)
    }

    @Test
    fun parsesDeviceAppLink() {
        val parsed = HuiLifeDeviceIdParser.parse(
            "ilife798s://i/device/nav?id=867404061163677"
        )
        assertEquals("867404061163677", parsed.deviceId)
    }

    @Test
    fun parsesQrDeviceNameAndAddr() {
        val json = JsonParser.parseString(
            """
            {
              "code": 0,
              "data": {
                "dev": {
                  "id": "dev_16_3f_01",
                  "name": "16栋3楼直饮水机",
                  "addr": "广兰校区16栋3层东"
                }
              }
            }
            """.trimIndent()
        ).asJsonObject
        val device = HuiLifeClient.parseQrDevice(json)
        assertEquals("dev_16_3f_01", device.id)
        assertEquals("16栋3楼直饮水机", device.name)
        assertEquals("广兰校区16栋3层东", device.detail)
    }

    @Test
    fun parsesDeviceStatusWithName() {
        val json = JsonParser.parseString(
            """
            {
              "code": 0,
              "data": {
                "device": {
                  "id": "867404061163677",
                  "devName": "16栋2楼热水机",
                  "location": "2楼西侧",
                  "status": 1,
                  "gene": { "status": 99, "time": 0 }
                }
              }
            }
            """.trimIndent()
        ).asJsonObject
        val status = HuiLifeClient.parseDeviceStatus(json)
        assertEquals("在线", status.label)
        assertEquals("16栋2楼热水机", status.deviceName)
        assertEquals("2楼西侧", status.deviceDetail)
    }

    @Test
    fun resolvesDistinctScannedDeviceNamesWithPriority() {
        assertEquals(
            "我的常用饮水机",
            HuiLifeViewModel.resolveDisplayName(
                id = "867404061163677",
                serverName = "16栋3楼直饮水机",
                customName = "我的常用饮水机"
            )
        )
        assertEquals(
            "16栋2楼热水机",
            HuiLifeViewModel.resolveDisplayName(
                id = "867404061163677",
                statusName = "16栋2楼热水机",
                billName = "旧账单名"
            )
        )
        val fallbackA = HuiLifeViewModel.resolveDisplayName(id = "867404061163677")
        val fallbackB = HuiLifeViewModel.resolveDisplayName(id = "867404061998821")
        assertNotEquals(fallbackA, fallbackB)
        assertTrue(fallbackA.contains("163677"))
        assertTrue(fallbackB.contains("998821"))

        // 服务端返回占位名“新注册设备”时，也必须带尾号区分。
        val newRegA = HuiLifeViewModel.resolveDisplayName(
            id = "28459222",
            cachedName = "新注册设备"
        )
        val newRegB = HuiLifeViewModel.resolveDisplayName(
            id = "28452987",
            cachedName = "新注册设备"
        )
        assertNotEquals(newRegA, newRegB)
        assertTrue(newRegA.contains("459222"))
        assertTrue(newRegB.contains("452987"))
    }

    @Test
    fun extractsSubDeviceNameWhenTopLevelIsPlaceholder() {
        val json = JsonParser.parseString(
            """
            {
              "id": "28452987",
              "name": "新注册设备",
              "ep": { "name": "东华理工大学广兰" },
              "subs": [
                { "id": "sub1", "name": "16栋2楼西吹风机" }
              ]
            }
            """.trimIndent()
        ).asJsonObject
        val device = HuiLifeClient.parseDeviceObject(json)
        assertEquals("16栋2楼西吹风机", device?.name)
        assertEquals(HuiLifeDeviceKind.HAIR_DRYER, device?.kind)
    }

    @Test
    fun distinguishesHairDryerFromWaterDispenserEvenWithSharedAreaSubtitle() {
        val dryer = HuiLifeDevice(
            id = "1",
            name = "16栋一楼西边左吹风机",
            detail = "东华理工大学广兰（饮水、开水、洗衣）",
            typeCode = "3"
        )
        val water = HuiLifeDevice(
            id = "2",
            name = "16栋1层西直饮水",
            detail = "东华理工大学广兰（饮水、开水、洗衣）",
            typeCode = "3"
        )
        val unknownBtype3 = HuiLifeDevice(
            id = "3",
            name = "未命名设备 · 452987",
            detail = "编号 452987",
            typeCode = "3"
        )
        val showerByArea = HuiLifeDevice(
            id = "4",
            name = "16-1-9",
            detail = "东华理工大学广兰（8-18栋淋浴）"
        )
        assertEquals(HuiLifeDeviceKind.HAIR_DRYER, dryer.kind)
        assertEquals(HuiLifeDeviceKind.WATER, water.kind)
        assertEquals(HuiLifeDeviceKind.OTHER, unknownBtype3.kind)
        assertEquals(HuiLifeDeviceKind.SHOWER, showerByArea.kind)
    }

    @Test
    fun onlyAllowsStoppingSelfStartedOrOwnedRunningDevices() {
        val now = 1_700_000_000_000L
        val runningOther = HuiLifeDeviceStatus(deviceStatus = 1, geneStatus = 5, usingUserId = "other_uid")
        val runningMineByServer = HuiLifeDeviceStatus(deviceStatus = 1, geneStatus = 5, usingUserId = "my_uid")
        val runningNoUid = HuiLifeDeviceStatus(deviceStatus = 1, geneStatus = 5)
        val idle = HuiLifeDeviceStatus(deviceStatus = 1, geneStatus = 99)
        val offline = HuiLifeDeviceStatus(deviceStatus = 0, geneStatus = 5)

        // 别人占用中的设备绝对不能停。
        assertFalse(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = true,
                status = runningOther,
                startedByMeMillis = null,
                myUid = "my_uid",
                nowMillis = now
            )
        )
        // 未登录设备控制端不能停。
        assertFalse(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = false,
                status = runningNoUid,
                startedByMeMillis = now - 5_000L,
                nowMillis = now
            )
        )
        // 空闲或离线不能停。
        assertFalse(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = true,
                status = idle,
                startedByMeMillis = now - 5_000L,
                nowMillis = now
            )
        )
        assertFalse(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = true,
                status = offline,
                startedByMeMillis = now - 5_000L,
                nowMillis = now
            )
        )
        // 本机 30 分钟内亲手启动且运行中 -> 允许停。
        assertTrue(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = true,
                status = runningNoUid,
                startedByMeMillis = now - 60_000L,
                nowMillis = now
            )
        )
        // 超过 30 分钟 TTL 不再允许停。
        assertFalse(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = true,
                status = runningNoUid,
                startedByMeMillis = now - 31 * 60 * 1000L,
                nowMillis = now
            )
        )
        // 服务端返回 usingUserId 匹配本人 uid -> 允许停。
        assertTrue(
            HuiLifeViewModel.shouldAllowStop(
                appLoggedIn = true,
                status = runningMineByServer,
                startedByMeMillis = null,
                myUid = "my_uid",
                nowMillis = now
            )
        )

        // 轮询清理：刚启动 5 秒内仍空闲不提前清理；超过 15 秒回到空闲自动清理。
        val kept = HuiLifeViewModel.reconcileStartedByMe(
            startedByMeAt = mapOf("d1" to now - 5_000L, "d2" to now - 25_000L),
            statuses = mapOf("d1" to idle, "d2" to idle),
            nowMillis = now
        )
        assertTrue("d1" in kept)
        assertFalse("d2" in kept)
    }
}
