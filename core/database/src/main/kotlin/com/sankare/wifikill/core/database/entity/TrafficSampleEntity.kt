package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traffic_samples",
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
    ],
    indices = [
        Index(value = ["deviceId", "sampledAt"]),
        Index(value = ["networkId", "sampledAt"]),
    ],
)
data class TrafficSampleEntity(
    @PrimaryKey
    val id: String,
    val deviceId: String? = null,
    val networkId: String,
    val source: String,
    val bytesReceived: Long? = null,
    val bytesSent: Long? = null,
    val sampledAt: Long,
)
