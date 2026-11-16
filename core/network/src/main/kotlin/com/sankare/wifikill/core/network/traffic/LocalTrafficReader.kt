package com.sankare.wifikill.core.network.traffic

data class LocalTrafficCounters(
    val bytesReceived: Long?,
    val bytesSent: Long?,
)

interface LocalTrafficReader {
    fun read(): LocalTrafficCounters
}
