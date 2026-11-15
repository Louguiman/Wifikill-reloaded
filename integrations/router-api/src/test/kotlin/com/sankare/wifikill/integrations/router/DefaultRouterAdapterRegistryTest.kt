package com.sankare.wifikill.integrations.router

import com.sankare.wifikill.core.domain.model.RouterAdapterType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultRouterAdapterRegistryTest {

    private val generic = mockk<RouterAdapter>(relaxed = true)
    private val openWrt = mockk<RouterAdapter>(relaxed = true)

    private val registry = DefaultRouterAdapterRegistry(
        mapOf(
            RouterAdapterType.OPENWRT to openWrt,
            RouterAdapterType.GENERIC to generic,
        ),
    )

    @Test
    fun `returns adapters in enum order`() {
        assertEquals(
            listOf(RouterAdapterType.OPENWRT, RouterAdapterType.GENERIC),
            registry.getSupportedAdapters(),
        )
    }

    @Test
    fun `returns adapter for supported type`() {
        assertSame(generic, registry.getAdapter(RouterAdapterType.GENERIC))
        assertSame(openWrt, registry.getAdapter(RouterAdapterType.OPENWRT))
    }

    @Test
    fun `returns null for unsupported type`() {
        assertNull(registry.getAdapter(RouterAdapterType.MIKROTIK))
    }
}
