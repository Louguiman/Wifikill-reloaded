package com.sankare.wifikill.integrations.generic

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionConfig
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.domain.repository.RouterCredential
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericRouterAdapterTest {

    private val dispatchers = object : DispatchersProvider {
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
        override val main = Dispatchers.Unconfined
    }

    @Test
    fun `connect verifies router information when authenticated`() = runBlocking {
        TestServer(status = 200).use { server ->
            val adapter = GenericRouterAdapter(
                credentialProvider = FakeCredentialProvider(),
                dispatchers = dispatchers,
            )

            val result = adapter.connect(config(server.port))

            assertTrue(result is AppResult.Success)
            assertEquals("127.0.0.1:${server.port}", (result as AppResult.Success).data.managementAddress)
            assertEquals(setOf(RouterCapability.ROUTER_INFORMATION), adapter.getCapabilities())
        }
    }

    @Test
    fun `connect fails with authentication error on unauthorized response`() = runBlocking {
        TestServer(status = 401).use { server ->
            val adapter = GenericRouterAdapter(
                credentialProvider = FakeCredentialProvider(),
                dispatchers = dispatchers,
            )

            val result = adapter.connect(config(server.port))

            assertEquals(AppErrorCode.AUTHENTICATION_FAILED, (result as AppResult.Failure).error.code)
            assertTrue(adapter.getCapabilities().isEmpty())
        }
    }

    @Test
    fun `connect reports unreachable when server is not listening`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val adapter = GenericRouterAdapter(
            credentialProvider = FakeCredentialProvider(),
            dispatchers = dispatchers,
        )

        val result = adapter.connect(config(port))

        assertEquals(AppErrorCode.ROUTER_UNREACHABLE, (result as AppResult.Failure).error.code)
        assertTrue(result.error.retryable)
    }

    @Test
    fun `connect propagates missing credential`() = runBlocking {
        val adapter = GenericRouterAdapter(
            credentialProvider = FakeCredentialProvider(credential = null),
            dispatchers = dispatchers,
        )

        val result = adapter.connect(config(1080))

        assertEquals(AppErrorCode.STORAGE_ERROR, (result as AppResult.Failure).error.code)
    }

    @Test
    fun `connect rejects unsupported protocol`() = runBlocking {
        val adapter = GenericRouterAdapter(
            credentialProvider = FakeCredentialProvider(),
            dispatchers = dispatchers,
        )

        val result = adapter.connect(config(1080, protocol = RouterProtocol.ROUTER_API))

        assertEquals(AppErrorCode.UNSUPPORTED_CAPABILITY, (result as AppResult.Failure).error.code)
    }

    @Test
    fun `disconnect clears verified capabilities`() = runBlocking {
        TestServer(status = 200).use { server ->
            val adapter = GenericRouterAdapter(
                credentialProvider = FakeCredentialProvider(),
                dispatchers = dispatchers,
            )
            adapter.connect(config(server.port))

            adapter.disconnect()

            assertTrue(adapter.getCapabilities().isEmpty())
        }
    }

    private fun config(
        port: Int,
        protocol: RouterProtocol = RouterProtocol.HTTP,
    ) = RouterConnectionConfig(
        routerId = "router-1",
        host = "127.0.0.1",
        port = port,
        protocol = protocol,
        credentialId = "credential-1",
    )

    private class FakeCredentialProvider(
        private val credential: RouterCredential? = RouterCredential("admin", "secret"),
    ) : CredentialProvider {
        override suspend fun save(credential: RouterCredential): AppResult<String> =
            AppResult.Success("credential-1")

        override suspend fun get(credentialId: String): AppResult<RouterCredential> =
            if (credential == null) {
                AppResult.Failure(AppError(AppErrorCode.STORAGE_ERROR, "missing"))
            } else {
                AppResult.Success(credential)
            }

        override suspend fun delete(credentialId: String): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class TestServer(private val status: Int) : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port: Int = serverSocket.localPort

        private val worker = thread(isDaemon = true, name = "test-router") {
            try {
                while (!serverSocket.isClosed) {
                    serverSocket.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = "ok"
                        val response = buildString {
                            append("HTTP/1.1 $status ${reason(status)}\r\n")
                            append("Content-Type: text/plain\r\n")
                            append("Content-Length: ${body.toByteArray().size}\r\n")
                            append("Connection: close\r\n")
                            append("\r\n")
                            append(body)
                        }
                        socket.getOutputStream().apply {
                            write(response.toByteArray(Charsets.UTF_8))
                            flush()
                        }
                    }
                }
            } catch (_: IOException) {
            }
        }

        override fun close() {
            serverSocket.close()
            worker.join(1_000)
        }

        private companion object {
            fun reason(status: Int): String = when (status) {
                200 -> "OK"
                401 -> "Unauthorized"
                else -> "Status"
            }
        }
    }
}
