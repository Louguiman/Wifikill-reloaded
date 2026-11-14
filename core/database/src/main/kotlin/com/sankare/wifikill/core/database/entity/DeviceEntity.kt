package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["customName"]),
        Index(value = ["hostname"]),
    ],
)
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    val customName: String? = null,
    val hostname: String? = null,
    val manufacturer: String? = null,
    val deviceType: String = "UNKNOWN",
    val identityConfidence: String,
    val isTrusted: Boolean = false,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
