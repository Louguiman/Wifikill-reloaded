package com.sankare.wifikill.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import java.net.Inet4Address
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidNetworkContextProvider(
    context: Context,
    private val permissionChecker: PermissionChecker,
) : NetworkContextProvider {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun observeCurrentNetwork(): Flow<NetworkContext?> = callbackFlow {
        trySend(readCurrentNetwork())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(readCurrentNetwork())
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(readCurrentNetwork())
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: SecurityException) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    private fun readCurrentNetwork(): NetworkContext? {
        if (!permissionChecker.hasNetworkStateAccess()) return null

        return try {
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            val linkProperties = connectivityManager.getLinkProperties(network)

            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.OTHER
            }

            val linkAddress = linkProperties
                ?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
            val localIp = linkAddress?.address?.hostAddress
            val prefixLength = linkAddress?.prefixLength
            val subnet = if (localIp != null && prefixLength != null) {
                "${networkAddress(localIp, prefixLength)}/$prefixLength"
            } else {
                null
            }
            val gatewayIp = linkProperties
                ?.routes
                ?.firstOrNull { it.isDefaultRoute }
                ?.gateway
                ?.hostAddress

            val wifi = if (type == NetworkType.WIFI) readWifiIdentity() else WifiIdentity()

            NetworkContext(
                networkId = wifi.bssid ?: wifi.ssid ?: gatewayIp ?: "network-${network.networkHandle}",
                ssid = wifi.ssid,
                bssid = wifi.bssid,
                gatewayIp = gatewayIp,
                localIp = localIp,
                subnet = subnet,
                interfaceName = linkProperties?.interfaceName,
                networkType = type,
                isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                dnsServers = linkProperties
                    ?.dnsServers
                    ?.mapNotNull { it.hostAddress }
                    .orEmpty(),
            )
        } catch (e: SecurityException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun readWifiIdentity(): WifiIdentity {
        if (!permissionChecker.hasWifiDetailsAccess()) return WifiIdentity()

        return try {
            val wifiManager =
                appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return WifiIdentity()
            val info = wifiManager.connectionInfo ?: return WifiIdentity()
            val ssid = info.ssid
                ?.removeSurrounding("\"")
                ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
            val bssid = info.bssid
                ?.takeUnless {
                    it.isBlank() ||
                        it == "02:00:00:00:00:00" ||
                        it == "00:00:00:00:00:00"
                }
            WifiIdentity(ssid = ssid, bssid = bssid)
        } catch (e: SecurityException) {
            WifiIdentity()
        }
    }

    private fun networkAddress(ip: String, prefixLength: Int): String? {
        val octets = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || prefixLength !in 0..32) return null
        val address = (octets[0] shl 24) or
            (octets[1] shl 16) or
            (octets[2] shl 8) or
            octets[3]
        val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
        val network = address and mask
        return listOf(
            (network ushr 24) and 0xFF,
            (network ushr 16) and 0xFF,
            (network ushr 8) and 0xFF,
            network and 0xFF,
        ).joinToString(".")
    }

    private data class WifiIdentity(
        val ssid: String? = null,
        val bssid: String? = null,
    )
}
