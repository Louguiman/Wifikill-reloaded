package com.sankare.wifikill.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_settings",
    indices = [Index(value = ["settingKey"], unique = true)],
)
data class AppSettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val settingKey: String,
    val settingValue: String? = null,
    val updatedAt: Long,
)
