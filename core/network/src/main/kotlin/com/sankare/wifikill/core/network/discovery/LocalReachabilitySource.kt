package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.ObservationSource
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class LocalReachabilitySource @Inject constructor(
    private val subnetScanner: SubnetScanner,
) : DiscoverySource {

    override val source: ObservationSource = ObservationSource.LOCAL_DISCOVERY

    override suspend fun isAvailable(context: NetworkContext): Boolean =
        context.localIp != null &&
            context.networkType != NetworkType.CELLULAR &&
            context.subnet != null

    override fun discover(context: NetworkContext): Flow<DiscoveredHost> = channelFlow {
        val hosts = subnetScanner.hosts(context)
        if (hosts.isEmpty()) {
            close()
            return@channelFlow
        }

        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        coroutineScope {
            hosts.forEach { ipAddress ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        probe(ipAddress)?.let { send(it) }
                    }
                }
            }
        }
    }

    private suspend fun probe(ipAddress: String): DiscoveredHost? = withContext(Dispatchers.IO) {
        val address = try {
            InetAddress.getByName(ipAddress)
        } catch (e: IOException) {
            return@withContext null
        }

        val startedAt = System.nanoTime()
        val respondsToEcho = try {
            address.isReachable(ECHO_TIMEOUT_MS)
        } catch (e: IOException) {
            false
        }

        val respondsToTcp = respondsToEcho || COMMON_PORTS.any { port -> canConnect(ipAddress, port) }
        if (!respondsToTcp) return@withContext null

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val hostname = try {
            address.hostName?.takeIf { it.isNotBlank() && it != ipAddress }
        } catch (e: IOException) {
            null
        }

        DiscoveredHost(
            ipAddress = ipAddress,
            hostname = hostname,
            responseTimeMs = elapsedMs,
        )
    }

    private fun canConnect(ipAddress: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ipAddress, port), TCP_TIMEOUT_MS)
            true
        }
    } catch (e: IOException) {
        false
    }

    private companion object {
        const val MAX_CONCURRENT_PROBES = 32
        const val ECHO_TIMEOUT_MS = 300
        const val TCP_TIMEOUT_MS = 120
        val COMMON_PORTS = listOf(80, 443, 22, 53, 445)
    }
}
