package com.sankare.wifikill.core.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {

    override fun hasNetworkStateAccess(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasWifiDetailsAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (nearby == PackageManager.PERMISSION_GRANTED) return true
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
}
