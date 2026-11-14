package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routers",
    foreignKeys = [
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["networkId"])],
)
data class RouterEntity(
    @PrimaryKey
    val id: String,
    val networkId: String,
    val name: String,
    val ipAddress: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmwareVersion: String? = null,
    val adapterType: String,
    val connectionStatus: String,
    val credentialId: String? = null,
    val lastConnectedAt: Long? = null,
    val createdAt: Long,
)
