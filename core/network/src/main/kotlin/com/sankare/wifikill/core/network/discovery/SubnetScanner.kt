package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.domain.model.NetworkContext

interface SubnetScanner {
    fun hosts(context: NetworkContext): List<String>
}

class DefaultSubnetScanner : SubnetScanner {

    override fun hosts(context: NetworkContext): List<String> {
        val localAddress = context.localIp?.let(::parseIpv4) ?: return emptyList()
        val (networkAddress, prefixLength) = parseCidr(context.subnet, localAddress)

        val effectivePrefix = maxOf(prefixLength, MIN_PREFIX_LENGTH)
        val mask = if (effectivePrefix == 0) 0 else -1 shl (32 - effectivePrefix)
        val network = (networkAddress and mask).toLong() and 0xFFFFFFFFL
        val hostCount = 1L shl (32 - effectivePrefix)

        val firstHost = network + 1L
        val lastHost = network + hostCount - 2L
        if (lastHost < firstHost) return emptyList()

        val localIp = context.localIp
        return (firstHost..lastHost)
            .asSequence()
            .map { formatIpv4(it.toInt()) }
            .filter { it != localIp }
            .take(MAX_HOSTS)
            .toList()
    }

    private fun parseCidr(subnet: String?, fallbackAddress: Int): Pair<Int, Int> {
        val parts = subnet?.split('/')
        if (parts != null && parts.size == 2) {
            val address = parseIpv4(parts[0])
            val prefix = parts[1].toIntOrNull()
            if (address != null && prefix != null && prefix in 0..32) {
                return address to prefix
            }
        }
        val mask = -1 shl 8
        return (fallbackAddress and mask) to 24
    }

    private fun parseIpv4(value: String): Int? {
        val octets = value.split('.')
        if (octets.size != 4) return null
        val parsed = octets.map { it.toIntOrNull()?.takeIf { part -> part in 0..255 } ?: return null }
        return (parsed[0] shl 24) or (parsed[1] shl 16) or (parsed[2] shl 8) or parsed[3]
    }

    private fun formatIpv4(value: Int): String = listOf(
        (value ushr 24) and 0xFF,
        (value ushr 16) and 0xFF,
        (value ushr 8) and 0xFF,
        value and 0xFF,
    ).joinToString(".")

    private companion object {
        const val MIN_PREFIX_LENGTH = 22
        const val MAX_HOSTS = 1024
    }
}
