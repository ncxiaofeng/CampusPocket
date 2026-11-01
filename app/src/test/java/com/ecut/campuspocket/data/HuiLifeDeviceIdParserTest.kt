package com.ecut.campuspocket.data

import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceIdParser
import org.junit.Assert.assertEquals
import org.junit.Test

class HuiLifeDeviceIdParserTest {

    @Test
    fun parsesCloudH5Url() {
        val raw = "https://cloud.hnkzy.com/h5/?atype=1&id=12345678#/pages/app/index"
        assertEquals("12345678", HuiLifeDeviceIdParser.normalize(raw))
    }

    @Test
    fun parsesIlifeScheme() {
        assertEquals(
            "abc12345",
            HuiLifeDeviceIdParser.normalize("ilife://device?did=abc12345")
        )
    }

    @Test
    fun parsesJsonPayload() {
        assertEquals(
            "87654321",
            HuiLifeDeviceIdParser.normalize("""{"did":"87654321","name":"饮水机"}""")
        )
    }

    @Test
    fun keepsRawDeviceId() {
        assertEquals("A1b2C3d4", HuiLifeDeviceIdParser.normalize("  A1b2C3d4  "))
    }

    @Test
    fun normalizesEncodedUrl() {
        val raw = "https%3A%2F%2Fcloud.hnkzy.com%2Fh5%2F%3Fid%3D99887766"
        assertEquals("99887766", HuiLifeDeviceIdParser.normalize(raw))
    }
}
