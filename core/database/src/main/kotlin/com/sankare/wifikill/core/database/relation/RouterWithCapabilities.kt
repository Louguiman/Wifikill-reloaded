package com.sankare.wifikill.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.sankare.wifikill.core.database.entity.RouterCapabilityEntity
import com.sankare.wifikill.core.database.entity.RouterEntity

data class RouterWithCapabilities(
    @Embedded
    val router: RouterEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routerId",
    )
    val capabilities: List<RouterCapabilityEntity>,
)
