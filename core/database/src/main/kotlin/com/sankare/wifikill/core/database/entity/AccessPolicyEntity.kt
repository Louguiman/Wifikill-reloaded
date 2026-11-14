package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "access_policies",
    foreignKeys = [
        ForeignKey(
            entity = RouterEntity::class,
            parentColumns = ["id"],
            childColumns = ["routerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["routerId", "status"]),
        Index(value = ["deviceId"]),
    ],
)
data class AccessPolicyEntity(
    @PrimaryKey
    val id: String,
    val routerId: String,
    val deviceId: String,
    val action: String,
    val status: String,
    val routerPolicyReference: String? = null,
    val reason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
)
