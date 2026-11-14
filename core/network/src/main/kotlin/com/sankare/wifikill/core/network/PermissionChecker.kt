package com.sankare.wifikill.core.network

interface PermissionChecker {
    fun hasNetworkStateAccess(): Boolean

    fun hasWifiDetailsAccess(): Boolean
}
