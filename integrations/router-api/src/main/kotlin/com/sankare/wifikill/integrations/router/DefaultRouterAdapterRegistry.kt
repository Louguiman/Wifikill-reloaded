package com.sankare.wifikill.integrations.router

import com.sankare.wifikill.core.domain.model.RouterAdapterType
import javax.inject.Inject

class DefaultRouterAdapterRegistry @Inject constructor(
    private val adapters: Map<RouterAdapterType, RouterAdapter>,
) : RouterAdapterRegistry {

    override fun getSupportedAdapters(): List<RouterAdapterType> =
        adapters.keys.sortedBy { it.ordinal }

    override fun getAdapter(type: RouterAdapterType): RouterAdapter? = adapters[type]
}
