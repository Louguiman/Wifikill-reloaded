package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_sessions",
    foreignKeys = [
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["networkId", "startedAt"])],
)
data class ScanSessionEntity(
    @PrimaryKey
    val id: String,
    val networkId: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: String,
    val devicesFound: Int = 0,
    val scanMethod: String,
    val errorMessage: String? = null,
)
