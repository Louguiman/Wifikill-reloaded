package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "router_capabilities",
    foreignKeys = [
        ForeignKey(
            entity = RouterEntity::class,
            parentColumns = ["id"],
            childColumns = ["routerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            value = ["routerId", "capabilityCode"],
            unique = true,
        ),
    ],
)
data class RouterCapabilityEntity(
    @PrimaryKey
    val id: String,
    val routerId: String,
    val capabilityCode: String,
    val isSupported: Boolean,
    val detectedAt: Long,
)
