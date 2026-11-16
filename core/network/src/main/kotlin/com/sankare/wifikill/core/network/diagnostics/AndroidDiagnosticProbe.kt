package com.sankare.wifikill.core.network.diagnostics

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AndroidDiagnosticProbe : DiagnosticProbe {

    override suspend fun ping(host: String, timeoutMs: Int): ProbeOutcome =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            val reachable = try {
                InetAddress.getByName(host).isReachable(timeoutMs)
            } catch (e: IOException) {
                false
            }
            ProbeOutcome(
                reachable = reachable,
                latencyMs = elapsedMs(startedAt),
                detail = if (reachable) "Reachable" else "No reachability response",
            )
        }

    override suspend fun tcpConnect(host: String, port: Int, timeoutMs: Int): ProbeOutcome =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            val reachable = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                true
            } catch (e: IOException) {
                false
            }
            ProbeOutcome(
                reachable = reachable,
                latencyMs = elapsedMs(startedAt),
                detail = if (reachable) "Connected to $host:$port" else "Could not connect to $host:$port",
            )
        }

    override suspend fun resolve(host: String, timeoutMs: Int): DnsOutcome =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            try {
                val addresses = withTimeout(timeoutMs.toLong()) {
                    InetAddress.getAllByName(host)
                }
                DnsOutcome(
                    resolved = addresses.isNotEmpty(),
                    addresses = addresses.mapNotNull { it.hostAddress },
                    latencyMs = elapsedMs(startedAt),
                )
            } catch (e: TimeoutCancellationException) {
                DnsOutcome(
                    resolved = false,
                    latencyMs = elapsedMs(startedAt),
                    detail = "DNS resolution timed out",
                )
            } catch (e: UnknownHostException) {
                DnsOutcome(
                    resolved = false,
                    latencyMs = elapsedMs(startedAt),
                    detail = "DNS resolution failed",
                )
            } catch (e: IOException) {
                DnsOutcome(
                    resolved = false,
                    latencyMs = elapsedMs(startedAt),
                    detail = e.message ?: "DNS resolution failed",
                )
            }
        }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
