package com.sankare.wifikill.integrations.openwrt

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class OpenWrtAdapterTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val recordedRequests = mutableListOf<String>()

    @Before
    fun setUp() {
        recordedRequests.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `connect authenticates and reports verified capabilities`() = runTest {
        server.dispatcher = ubusDispatcher()
        val adapter = adapter()

        val result = adapter.connect(config())

        assertTrue(result is AppResult.Success)
        val info = (result as AppResult.Success).data
        assertEquals("OpenWrt", info.manufacturer)
        assertEquals("OpenWrt One", info.model)
        assertEquals("OpenWrt 23.05.5", info.firmwareVersion)
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
    }

    @Test
    fun `connect rejects invalid credentials`() = runTest {
        server.dispatcher = ubusDispatcher(loginStatus = 6)
        val adapter = adapter()

        val result = adapter.connect(config())

        assertEquals(
            AppErrorCode.AUTHENTICATION_FAILED,
            (result as AppResult.Failure).error.code,
        )
    }

    @Test
    fun `discoverDevices maps dhcp leases and leaves missing fields null`() = runTest {
        server.dispatcher = ubusDispatcher()
        val adapter = adapter()
        adapter.connect(config())

        val result = adapter.discoverDevices()

        val devices = (result as AppResult.Success).data
        assertEquals(1, devices.size)
        val device = devices.single()
        assertEquals("aa:bb:cc:dd:ee:ff", device.macAddress)
        assertEquals("192.168.1.10", device.ipAddress)
        assertEquals("phone", device.hostname)
        assertEquals(true, device.isActive)
        assertEquals("DHCP", device.connectionType)
        assertNotNull(device.leaseExpiresAt)
    }

    @Test
    fun `applyAccessPolicy blocks by mac and returns a stable reference`() = runTest {
        server.dispatcher = ubusDispatcher()
        val adapter = adapter()
        adapter.connect(config())

        val result = adapter.applyAccessPolicy(
            AccessPolicyRequest(
                routerId = "router-1",
                deviceId = "device-1",
                action = AccessPolicyAction.BLOCK_INTERNET,
                reason = "test",
                expiresAt = null,
                targetMacAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )

        assertTrue(result is AppResult.Success)
        val receipt = (result as AppResult.Success).data
        assertTrue(receipt.routerPolicyReference!!.startsWith("wifikill_"))
        val requests = drainRequests()
        assertTrue(requests.any { it.contains("\"uci\"") && it.contains("\"set\"") })
        assertTrue(requests.any { it.contains("\"commit\"") })
        assertTrue(requests.any { it.contains("\"apply\"") })
        assertTrue(requests.any { it.contains("\"src_mac\":\"aa:bb:cc:dd:ee:ff\"") })
    }

    @Test
    fun `verifyAccessPolicy only verifies when router state matches`() = runTest {
        server.dispatcher = ubusDispatcher()
        val adapter = adapter()
        adapter.connect(config())
        val request = PolicyVerificationRequest(
            operationId = "op",
            routerPolicyReference = "wifikill_device1",
            expectedAction = AccessPolicyAction.BLOCK_INTERNET,
            deviceId = "device-1",
            targetMacAddress = "aa:bb:cc:dd:ee:ff",
        )

        val verified = adapter.verifyAccessPolicy(request)
        assertTrue((verified as AppResult.Success).data.isVerified)

        val allowed = adapter.verifyAccessPolicy(
            request.copy(expectedAction = AccessPolicyAction.ALLOW_INTERNET),
        )
        assertFalse((allowed as AppResult.Success).data.isVerified)
    }

    @Test
    fun `removeAccessPolicy deletes the referenced firewall rule`() = runTest {
        server.dispatcher = ubusDispatcher()
        val adapter = adapter()
        adapter.connect(config())
        drainRequests()

        val result = adapter.removeAccessPolicy("wifikill_device1")

        assertTrue(result is AppResult.Success)
        val requests = drainRequests()
        assertTrue(requests.any { it.contains("\"delete\"") && it.contains("wifikill_device1") })
        assertTrue(requests.any { it.contains("\"commit\"") })
    }

    private fun adapter(): OpenWrtAdapter = OpenWrtAdapter(
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

    private fun drainRequests(): List<String> {
        val requests = recordedRequests.toList()
        recordedRequests.clear()
        return requests
    }

    private fun ubusDispatcher(loginStatus: Int = 0): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.method != "POST") return MockResponse().setResponseCode(404)
            val rawBody = request.body.readUtf8()
            recordedRequests += rawBody
            val body = json.parseToJsonElement(rawBody).jsonObject
            val params = body["params"]!!.jsonArray
            val objectName = params[1].jsonPrimitive.content
            val method = params[2].jsonPrimitive.content
            val payload = when {
                objectName == "session" && method == "login" -> {
                    if (loginStatus != 0) {
                        return response(loginStatus, buildJsonObject { })
                    }
                    buildJsonObject { put("ubus_rpc_session", "session-token") }
                }

                objectName == "system" && method == "board" -> buildJsonObject {
                    put("model", "OpenWrt One")
                    put("hostname", "router")
                }

                objectName == "system" && method == "info" -> buildJsonObject {
                    put("kernel", "6.6.50")
                    put("hostname", "router")
                    put(
                        "release",
                        buildJsonObject {
                            put("description", "OpenWrt 23.05.5")
                            put("version", "23.05.5")
                        },
                    )
                }

                objectName == "dhcp" && method == "ipv4leases" -> buildJsonObject {
                    put(
                        "device",
                        buildJsonObject {
                            put(
                                "br-lan",
                                buildJsonObject {
                                    put(
                                        "leases",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("mac", "aa:bb:cc:dd:ee:ff")
                                                    put("ip", "192.168.1.10")
                                                    put("hostname", "phone")
                                                    put("valid", true)
                                                    put("expires", 3600)
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }

                objectName == "uci" && method == "get" -> {
                    val callParams = params[3].jsonObject
                    val sectionValues = buildJsonObject {
                        put("name", "wifikill-block-device1")
                        put("src_mac", "aa:bb:cc:dd:ee:ff")
                        put("target", "REJECT")
                        put("enabled", "1")
                    }
                    if (callParams.containsKey("section")) {
                        buildJsonObject { put("values", sectionValues) }
                    } else {
                        buildJsonObject {
                            put(
                                "values",
                                buildJsonObject { put("wifikill_device1", sectionValues) },
                            )
                        }
                    }
                }

                objectName == "uci" -> buildJsonObject { }
                else -> return MockResponse().setResponseCode(404)
            }
            return response(0, payload)
        }
    }

    private fun response(status: Int, payload: JsonObject): MockResponse {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put(
                "result",
                buildJsonArray {
                    add(JsonPrimitive(status))
                    add(payload)
                },
            )
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body.toString())
    }

    private class FakeCredentialProvider : CredentialProvider {
        override suspend fun save(credential: RouterCredential): AppResult<String> =
            AppResult.Success("credential-1")

        override suspend fun get(credentialId: String): AppResult<RouterCredential> =
            AppResult.Success(RouterCredential("root", "secret"))

        override suspend fun delete(credentialId: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
