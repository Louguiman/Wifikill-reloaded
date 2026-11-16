package com.sankare.wifikill.core.network.traffic

import android.net.TrafficStats

class AndroidLocalTrafficReader : LocalTrafficReader {

    override fun read(): LocalTrafficCounters = LocalTrafficCounters(
        bytesReceived = TrafficStats.getTotalRxBytes().takeIf { it.isSupported() },
        bytesSent = TrafficStats.getTotalTxBytes().takeIf { it.isSupported() },
    )

    private fun Long.isSupported(): Boolean = this >= 0 && this != TrafficStats.UNSUPPORTED.toLong()
}
