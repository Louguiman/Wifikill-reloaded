package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.ObservationSource
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDiscoveryEngineTest {

    private val context = NetworkContext(
        networkId = "network",
        ssid = "test",
        bssid = null,
        gatewayIp = "192.168.1.1",
        localIp = "192.168.1.42",
        subnet = "192.168.1.0/24",
        interfaceName = "wlan0",
        networkType = NetworkType.WIFI,
        isMetered = false,
        isValidated = true,
    )

    @Test
    fun `emits hosts and completion for available source`() = runTest {
        val source = FakeSource(
            hosts = listOf(
                DiscoveredHost(ipAddress = "192.168.1.1"),
                DiscoveredHost(ipAddress = "192.168.1.2"),
            ),
        )
        val engine = DefaultDiscoveryEngine(setOf(source))

        val events = engine.discover(context, setOf(source.source), timeoutMs = 1_000).toList()

        assertTrue(events.first() is DiscoveryEvent.SourceStarted)
        assertEquals(2, events.filterIsInstance<DiscoveryEvent.HostFound>().size)
        val completed = events.filterIsInstance<DiscoveryEvent.SourceCompleted>().single()
        assertEquals(2, completed.devicesFound)
    }

    @Test
    fun `reports unavailable source without discovering`() = runTest {
        val source = FakeSource(hosts = listOf(DiscoveredHost(ipAddress = "192.168.1.1")), available = false)
        val engine = DefaultDiscoveryEngine(setOf(source))

        val events = engine.discover(context, setOf(source.source), timeoutMs = 1_000).toList()

        assertTrue(events.any { it is DiscoveryEvent.SourceUnavailable })
        assertTrue(events.none { it is DiscoveryEvent.HostFound })
    }

    @Test
    fun `maps source failures without failing the scan`() = runTest {
        val source = object : DiscoverySource {
            override val source = ObservationSource.LOCAL_DISCOVERY

            override suspend fun isAvailable(context: NetworkContext): Boolean = true

            override fun discover(context: NetworkContext): Flow<DiscoveredHost> = flow {
                throw IOException("probe failed")
            }
        }
        val engine = DefaultDiscoveryEngine(setOf(source))

        val events = engine.discover(context, setOf(source.source), timeoutMs = 1_000).toList()

        val failure = events.filterIsInstance<DiscoveryEvent.SourceFailed>().single()
        assertEquals(AppErrorCode.NETWORK_UNAVAILABLE, failure.error.code)
    }

    @Test
    fun `times out a slow source`() = runTest {
        val source = object : DiscoverySource {
            override val source = ObservationSource.LOCAL_DISCOVERY

            override suspend fun isAvailable(context: NetworkContext): Boolean = true

            override fun discover(context: NetworkContext): Flow<DiscoveredHost> = flow {
                delay(10_000)
                emit(DiscoveredHost(ipAddress = "192.168.1.1"))
            }
        }
        val engine = DefaultDiscoveryEngine(setOf(source))

        val events = engine.discover(context, setOf(source.source), timeoutMs = 100).toList()

        val failure = events.filterIsInstance<DiscoveryEvent.SourceFailed>().single()
        assertEquals(AppErrorCode.SCAN_TIMEOUT, failure.error.code)
    }

    private class FakeSource(
        private val hosts: List<DiscoveredHost>,
        private val available: Boolean = true,
    ) : DiscoverySource {
        override val source: ObservationSource = ObservationSource.LOCAL_DISCOVERY

        override suspend fun isAvailable(context: NetworkContext): Boolean = available

        override fun discover(context: NetworkContext): Flow<DiscoveredHost> = flowOf(*hosts.toTypedArray())
    }
}
