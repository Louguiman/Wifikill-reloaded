package com.sankare.wifikill.navigation

object Destinations {
    const val DASHBOARD = "dashboard"
    const val DISCOVERY = "discovery"
    const val DEVICES = "devices"
    const val DEVICE_DETAIL = "devices/detail/{deviceId}"
    const val DEVICE_ID_ARG = "deviceId"
    const val MONITORING = "monitoring"
    const val ACCESS = "access"
    const val ROUTERS = "routers"
    const val ROUTER_ADD = "routers/add"
    const val DIAGNOSTICS = "diagnostics"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun deviceDetail(deviceId: String): String = "devices/detail/$deviceId"
}
