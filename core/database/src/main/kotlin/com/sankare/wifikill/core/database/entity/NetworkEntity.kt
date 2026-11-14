package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "networks",
    indices = [Index(value = ["ssid"])],
)
data class NetworkEntity(
    @PrimaryKey
    val id: String,
    val ssid: String? = null,
    val bssid: String? = null,
    val gatewayIp: String? = null,
    val subnet: String? = null,
    val interfaceName: String? = null,
    val networkType: String,
    val isCurrent: Boolean = false,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)
