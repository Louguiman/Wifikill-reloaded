package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSubnetScannerTest {

    private val scanner = DefaultSubnetScanner()

    @Test
    fun `generates usable hosts for a twenty-four subnet`() {
        val hosts = scanner.hosts(context(localIp = "192.168.1.42", subnet = "192.168.1.0/24"))

        assertEquals(253, hosts.size)
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
        assertFalse(hosts.contains("192.168.1.42"))
        assertFalse(hosts.contains("192.168.1.0"))
        assertFalse(hosts.contains("192.168.1.255"))
    }

    @Test
    fun `caps large subnets to a twenty-two prefix`() {
        val hosts = scanner.hosts(context(localIp = "10.0.0.5", subnet = "10.0.0.0/16"))

        assertEquals(1021, hosts.size)
        assertTrue(hosts.contains("10.0.0.1"))
        assertTrue(hosts.contains("10.0.3.254"))
        assertFalse(hosts.contains("10.0.0.5"))
    }

    @Test
    fun `falls back to a twenty-four subnet when subnet is missing`() {
        val hosts = scanner.hosts(context(localIp = "192.168.0.10", subnet = null))

        assertEquals(253, hosts.size)
        assertTrue(hosts.contains("192.168.0.1"))
        assertTrue(hosts.contains("192.168.0.254"))
    }

    @Test
    fun `returns no hosts when local ip is missing`() {
        val hosts = scanner.hosts(context(localIp = null, subnet = null))

        assertTrue(hosts.isEmpty())
    }

    private fun context(localIp: String?, subnet: String?) = NetworkContext(
        networkId = "network",
        ssid = "test",
        bssid = null,
        gatewayIp = "192.168.1.1",
        localIp = localIp,
        subnet = subnet,
        interfaceName = "wlan0",
        networkType = NetworkType.WIFI,
        isMetered = false,
        isValidated = true,
    )
}
