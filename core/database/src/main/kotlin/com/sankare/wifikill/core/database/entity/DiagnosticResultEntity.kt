package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diagnostic_results",
    foreignKeys = [
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["networkId", "executedAt"])],
)
data class DiagnosticResultEntity(
    @PrimaryKey
    val id: String,
    val networkId: String,
    val diagnosticType: String,
    val target: String? = null,
    val status: String,
    val latencyMs: Long? = null,
    val resultMessage: String? = null,
    val executedAt: Long,
)
