package com.sankare.wifikill.core.network.diagnostics

data class ProbeOutcome(
    val reachable: Boolean,
    val latencyMs: Long?,
    val detail: String? = null,
)

data class DnsOutcome(
    val resolved: Boolean,
    val addresses: List<String> = emptyList(),
    val latencyMs: Long? = null,
    val detail: String? = null,
)

interface DiagnosticProbe {
    suspend fun ping(host: String, timeoutMs: Int): ProbeOutcome

    suspend fun tcpConnect(host: String, port: Int, timeoutMs: Int): ProbeOutcome

    suspend fun resolve(host: String, timeoutMs: Int): DnsOutcome
}
