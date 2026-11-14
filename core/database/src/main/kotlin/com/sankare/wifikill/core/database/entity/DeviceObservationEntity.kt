package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_observations",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["deviceId", "observedAt"]),
        Index(value = ["networkId", "observedAt"]),
        Index(value = ["macAddress"]),
        Index(value = ["scanSessionId"]),
    ],
)
data class DeviceObservationEntity(
    @PrimaryKey
    val id: String,
    val deviceId: String,
    val networkId: String,
    val scanSessionId: String? = null,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val hostname: String? = null,
    val manufacturer: String? = null,
    val source: String,
    val status: String,
    val responseTimeMs: Long? = null,
    val observedAt: Long,
)
