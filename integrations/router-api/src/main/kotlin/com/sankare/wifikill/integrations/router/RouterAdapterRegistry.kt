package com.sankare.wifikill.integrations.router

import com.sankare.wifikill.core.domain.model.RouterAdapterType

interface RouterAdapterRegistry {
    fun getSupportedAdapters(): List<RouterAdapterType>

    fun getAdapter(type: RouterAdapterType): RouterAdapter?
}
