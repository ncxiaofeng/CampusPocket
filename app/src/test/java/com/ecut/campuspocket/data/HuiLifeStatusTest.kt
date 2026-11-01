package com.ecut.campuspocket.data

import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceIdParser
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
