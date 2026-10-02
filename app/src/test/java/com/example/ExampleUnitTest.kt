package com.example

import com.example.data.parser.V2RayParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testVlessParsing() {
        val vlessUri = "vless://12345678-1234-1234-1234-123456789abc@example.com:443?security=tls&type=ws&path=/vless#MyTestNode"
        val config = V2RayParser.parseSingleConfig(vlessUri, "http://test.url", "Test Source")
        assertNotNull(config)
        assertEquals("VLESS", config?.protocol)
        assertEquals("example.com", config?.host)
        assertEquals(443, config?.port)
        assertEquals("MyTestNode", config?.remark)
        assertEquals("tls", config?.security)
        assertEquals("ws", config?.network)
    }

    @Test
    fun testTrojanParsing() {
        val trojanUri = "trojan://password123@trojan.example.com:8443?security=tls#FastTrojan"
        val config = V2RayParser.parseSingleConfig(trojanUri, "http://test.url", "Test Source")
        assertNotNull(config)
        assertEquals("Trojan", config?.protocol)
        assertEquals("trojan.example.com", config?.host)
        assertEquals(8443, config?.port)
        assertEquals("FastTrojan", config?.remark)
    }

    @Test
    fun testShadowsocksParsing() {
        val ssUri = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNzd29yZDEyMw@1.2.3.4:8388#SSNode"
        val config = V2RayParser.parseSingleConfig(ssUri, "http://test.url", "Test Source")
        assertNotNull(config)
        assertEquals("Shadowsocks", config?.protocol)
        assertEquals("1.2.3.4", config?.host)
        assertEquals(8388, config?.port)
        assertEquals("SSNode", config?.remark)
    }
}
