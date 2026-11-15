package com.sankare.wifikill.integrations.mikrotik

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.PolicyVerificationRequest
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionConfig
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.domain.repository.RouterCredential
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MikroTikAdapterTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val recorded = mutableListOf<String>()
    private val addressEntries = mutableListOf<JsonObject>()
    private val filterRules = mutableListOf<JsonObject>()
    private val ids = AtomicInteger(0)

    @Before
    fun setUp() {
        recorded.clear()
        addressEntries.clear()
        filterRules.clear()
        ids.set(0)
        server = MockWebServer()
        server.dispatcher = dispatcher()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `connect validates rest session and reports verified capabilities`() = runTest {
        val adapter = adapter()

        val result = adapter.connect(config())

        assertTrue(result is AppResult.Success)
        val info = (result as AppResult.Success).data
        assertEquals("MikroTik", info.manufacturer)
        assertEquals("RB5009UG+S+", info.model)
        assertEquals("7.15.3", info.firmwareVersion)
        assertEquals("router", info.hostname)
        assertEquals(
            setOf(
                RouterCapability.ROUTER_INFORMATION,
                RouterCapability.DEVICE_DISCOVERY,
                RouterCapability.DHCP_CLIENTS,
                RouterCapability.ACCESS_POLICY_READ,
                RouterCapability.ACCESS_POLICY_WRITE,
            ),
            adapter.getCapabilities(),
        )
        assertTrue(addressEntries.isEmpty())
    }

    @Test
    fun `connect rejects invalid credentials`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(401)
        }
        val adapter = adapter()

        val result = adapter.connect(config())

        assertEquals(
            AppErrorCode.AUTHENTICATION_FAILED,
            (result as AppResult.Failure).error.code,
        )
    }

    @Test
    fun `discoverDevices maps dhcp leases`() = runTest {
        val adapter = adapter()
        adapter.connect(config())

        val result = adapter.discoverDevices()

        val devices = (result as AppResult.Success).data
        assertEquals(1, devices.size)
        val device = devices.single()
        assertEquals("*1", device.externalId)
        assertEquals("192.168.1.20", device.ipAddress)
        assertEquals("aa:bb:cc:dd:ee:01", device.macAddress)
        assertEquals("laptop", device.hostname)
        assertEquals("dhcp1", device.interfaceName)
        assertEquals(true, device.isActive)
    }

    @Test
    fun `applyAccessPolicy blocks by ip and installs a managed filter`() = runTest {
        val adapter = adapter()
        adapter.connect(config())

        val result = adapter.applyAccessPolicy(
            AccessPolicyRequest(
                routerId = "router-1",
                deviceId = "device-1",
                action = AccessPolicyAction.BLOCK_INTERNET,
                reason = "test",
                expiresAt = null,
                targetIpAddress = "192.168.1.20",
            ),
        )

        assertTrue(result is AppResult.Success)
        val receipt = (result as AppResult.Success).data
        assertNotNull(receipt.routerPolicyReference)
        assertEquals(1, addressEntries.size)
        assertEquals("wifikill-blocked", addressEntries.single()["list"].toString().trim('"'))
        assertTrue(filterRules.any { it["comment"].toString().contains("wifikill-managed-block") })
    }

    @Test
    fun `applyAccessPolicy is idempotent for an existing block`() = runTest {
        val adapter = adapter()
        adapter.connect(config())
        filterRules += buildJsonObject {
            put(".id", "*10")
            put("chain", "forward")
            put("action", "drop")
            put("src-address-list", "wifikill-blocked")
            put("comment", "wifikill-managed-block")
        }
        addressEntries += buildJsonObject {
            put(".id", "*20")
            put("list", "wifikill-blocked")
            put("address", "192.168.1.20/32")
        }
        recorded.clear()

        val result = adapter.applyAccessPolicy(
            AccessPolicyRequest(
                routerId = "router-1",
                deviceId = "device-1",
                action = AccessPolicyAction.BLOCK_INTERNET,
                reason = null,
                expiresAt = null,
                targetIpAddress = "192.168.1.20",
            ),
        )

        assertEquals("*20", (result as AppResult.Success).data.routerPolicyReference)
        assertEquals(1, addressEntries.size)
        assertFalse(recorded.any { it.contains("\"PUT\"") })
    }

    @Test
    fun `verifyAccessPolicy requires entry and filter for block`() = runTest {
        val adapter = adapter()
        adapter.connect(config())
        val request = PolicyVerificationRequest(
            operationId = "op",
            routerPolicyReference = "*20",
            expectedAction = AccessPolicyAction.BLOCK_INTERNET,
            deviceId = "device-1",
            targetIpAddress = "192.168.1.20",
        )

        addressEntries += buildJsonObject {
            put(".id", "*20")
            put("list", "wifikill-blocked")
            put("address", "192.168.1.20/32")
        }
        val withoutFilter = adapter.verifyAccessPolicy(request)
        assertFalse((withoutFilter as AppResult.Success).data.isVerified)

        filterRules += buildJsonObject {
            put(".id", "*10")
            put("chain", "forward")
            put("action", "drop")
            put("src-address-list", "wifikill-blocked")
            put("comment", "wifikill-managed-block")
        }
        val withFilter = adapter.verifyAccessPolicy(request)
        assertTrue((withFilter as AppResult.Success).data.isVerified)

        val allow = adapter.verifyAccessPolicy(
            request.copy(expectedAction = AccessPolicyAction.ALLOW_INTERNET),
        )
        assertFalse((allow as AppResult.Success).data.isVerified)
    }

    @Test
    fun `removeAccessPolicy deletes the address list entry`() = runTest {
        val adapter = adapter()
        adapter.connect(config())
        addressEntries += buildJsonObject {
            put(".id", "*20")
            put("list", "wifikill-blocked")
            put("address", "192.168.1.20/32")
        }

        val result = adapter.removeAccessPolicy("*20")

        assertTrue(result is AppResult.Success)
        assertTrue(addressEntries.isEmpty())
    }

    @Test
    fun `applyAccessPolicy requires a known ip address`() = runTest {
        val adapter = adapter()
        adapter.connect(config())

        val result = adapter.applyAccessPolicy(
            AccessPolicyRequest(
                routerId = "router-1",
                deviceId = "device-1",
                action = AccessPolicyAction.BLOCK_INTERNET,
                reason = null,
                expiresAt = null,
                targetMacAddress = "aa:bb:cc:dd:ee:01",
            ),
        )

        assertEquals(
            AppErrorCode.UNSUPPORTED_CAPABILITY,
            (result as AppResult.Failure).error.code,
        )
    }

    private fun adapter(): MikroTikAdapter = MikroTikAdapter(
        credentialProvider = FakeCredentialProvider(),
        dispatchers = object : DispatchersProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        },
        timeProvider = object : TimeProvider {
            override fun nowMillis(): Long = 1_700_000_000_000
        },
    )

    private fun config(): RouterConnectionConfig = RouterConnectionConfig(
        routerId = "router-1",
        host = "127.0.0.1",
        port = server.port,
        protocol = RouterProtocol.HTTP,
        credentialId = "credential-1",
    )

    private fun dispatcher(): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val method = request.method ?: "GET"
            val path = request.path?.substringBefore('?') ?: return MockResponse().setResponseCode(400)
            val body = request.body.readUtf8()
            recorded += "$method $path $body"

            return when {
                path == "/rest/system/resource" && method == "GET" -> jsonResponse(
                    buildJsonObject {
                        put("board-name", "RB5009UG+S+")
                        put("architecture-name", "arm64")
                        put("version", "7.15.3")
                    },
                )

                path == "/rest/system/identity" && method == "GET" -> jsonResponse(
                    buildJsonObject { put("name", "router") },
                )

                path == "/rest/ip/dhcp-server/lease" && method == "GET" -> jsonResponse(
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(".id", "*1")
                                put("address", "192.168.1.20")
                                put("mac-address", "aa:bb:cc:dd:ee:01")
                                put("host-name", "laptop")
                                put("status", "bound")
                                put("server", "dhcp1")
                            },
                        )
                    },
                )

                path == "/rest/ip/firewall/address-list" && method == "GET" -> jsonResponse(
                    buildJsonArray {
                        addressEntries.forEach { add(it) }
                    },
                )

                path == "/rest/ip/firewall/address-list" && method == "PUT" -> {
                    val payload = json.parseToJsonElement(body).jsonObject
                    val id = "*${ids.incrementAndGet()}"
                    val entry = buildJsonObject {
                        put(".id", id)
                        put("list", payload["list"].toString().trim('"'))
                        put("address", payload["address"].toString().trim('"'))
                        payload["comment"]?.let { put("comment", it) }
                    }
                    addressEntries += entry
                    jsonResponse(entry)
                }

                path.startsWith("/rest/ip/firewall/address-list/") && method == "DELETE" -> {
                    val id = path.substringAfterLast('/')
                    addressEntries.removeAll { it[".id"].toString().trim('"') == id }
                    MockResponse().setResponseCode(200)
                }

                path == "/rest/ip/firewall/filter" && method == "GET" -> jsonResponse(
                    buildJsonArray {
                        filterRules.forEach { add(it) }
                    },
                )

                path == "/rest/ip/firewall/filter" && method == "PUT" -> {
                    val payload = json.parseToJsonElement(body).jsonObject
                    val rule = buildJsonObject {
                        put(".id", "*${ids.incrementAndGet()}")
                        put("chain", payload["chain"].toString().trim('"'))
                        put("action", payload["action"].toString().trim('"'))
                        put("src-address-list", payload["src-address-list"].toString().trim('"'))
                        payload["comment"]?.let { put("comment", it) }
                    }
                    filterRules += rule
                    jsonResponse(rule)
                }

                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun jsonResponse(element: JsonElement): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(element.toString())

    private class FakeCredentialProvider : CredentialProvider {
        override suspend fun save(credential: RouterCredential): AppResult<String> =
            AppResult.Success("credential-1")

        override suspend fun get(credentialId: String): AppResult<RouterCredential> =
            AppResult.Success(RouterCredential("admin", "secret"))

        override suspend fun delete(credentialId: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
