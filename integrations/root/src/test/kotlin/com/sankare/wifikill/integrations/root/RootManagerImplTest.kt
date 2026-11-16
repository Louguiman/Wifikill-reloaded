package com.sankare.wifikill.integrations.root

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.domain.model.RootCapability
import com.sankare.wifikill.core.domain.model.RootOperation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootManagerImplTest {

    private val runner = FakeRootCommandRunner()
    private val timeProvider = object : TimeProvider {
        override fun nowMillis(): Long = NOW
    }
    private val manager = RootManagerImpl(runner, timeProvider)

    @Test
    fun `detects root from uid output`() = runTest {
        runner.result = RootCommandResult(exitCode = 0, output = "uid=0(root) gid=0(root)")

        assertTrue(manager.isAvailable())
        assertEquals(listOf("id"), runner.commands)
    }

    @Test
    fun `reports unavailable when root is denied`() = runTest {
        runner.result = RootCommandResult(exitCode = 1, output = "permission denied")

        assertFalse(manager.isAvailable())
        assertTrue(manager.getCapabilities().isEmpty())
    }

    @Test
    fun `executes only the approved interface inspection command`() = runTest {
        runner.results += RootCommandResult(0, "uid=0(root)")
        runner.results += RootCommandResult(0, "1: lo: <LOOPBACK>")

        val result = manager.execute(RootOperation.INSPECT_LOCAL_INTERFACE)

        assertEquals(
            listOf("id", "ip addr show"),
            runner.commands,
        )
        val success = result as AppResult.Success
        assertEquals(RootOperation.INSPECT_LOCAL_INTERFACE, success.data.operation)
        assertEquals("1: lo: <LOOPBACK>", success.data.output)
        assertEquals(NOW, success.data.executedAt)
    }

    @Test
    fun `executes only the approved configuration read command`() = runTest {
        runner.results += RootCommandResult(0, "uid=0(root)")
        runner.results += RootCommandResult(0, "default via 192.168.1.1")

        val result = manager.execute(RootOperation.READ_NETWORK_CONFIGURATION)

        assertEquals(listOf("id", "ip route show"), runner.commands)
        assertEquals(
            "default via 192.168.1.1",
            (result as AppResult.Success).data.output,
        )
    }

    @Test
    fun `execution fails before running an operation when root is unavailable`() = runTest {
        runner.result = RootCommandResult(1, "denied")

        val result = manager.execute(RootOperation.INSPECT_LOCAL_INTERFACE)

        assertEquals(
            AppErrorCode.ROOT_UNAVAILABLE,
            (result as AppResult.Failure).error.code,
        )
        assertEquals(listOf("id"), runner.commands)
    }

    @Test
    fun `non-zero exit code is reported as failure`() = runTest {
        runner.results += RootCommandResult(0, "uid=0(root)")
        runner.results += RootCommandResult(2, "ip: not found")

        val result = manager.execute(RootOperation.READ_NETWORK_CONFIGURATION)

        val failure = result as AppResult.Failure
        assertEquals(AppErrorCode.ROOT_OPERATION_FAILED, failure.error.code)
        assertTrue(failure.error.retryable)
    }

    @Test
    fun `timeout is reported as a retryable failure`() = runTest {
        runner.results += RootCommandResult(0, "uid=0(root)")
        runner.results += RootCommandResult(
            exitCode = SuRootCommandRunner.TIMEOUT_EXIT_CODE,
            output = null,
            timedOut = true,
        )

        val result = manager.execute(RootOperation.INSPECT_LOCAL_INTERFACE)

        val failure = result as AppResult.Failure
        assertEquals(AppErrorCode.ROOT_OPERATION_FAILED, failure.error.code)
        assertTrue(failure.error.retryable)
    }

    @Test
    fun `blank output is stored as null and never invented`() = runTest {
        runner.results += RootCommandResult(0, "uid=0(root)")
        runner.results += RootCommandResult(0, "   ")

        val result = manager.execute(RootOperation.INSPECT_LOCAL_INTERFACE)

        assertNull((result as AppResult.Success).data.output)
    }

    @Test
    fun `caps oversized output`() = runTest {
        runner.results += RootCommandResult(0, "uid=0(root)")
        runner.results += RootCommandResult(0, "x".repeat(20_000))

        val result = manager.execute(RootOperation.INSPECT_LOCAL_INTERFACE)

        assertEquals(
            RootManagerImpl.MAX_RESULT_CHARS,
            (result as AppResult.Success).data.output?.length,
        )
    }

    @Test
    fun `reports the fixed capability set only when root is available`() = runTest {
        runner.result = RootCommandResult(0, "uid=0(root)")

        assertEquals(
            setOf(
                RootCapability.ADVANCED_NETWORK_DIAGNOSTICS,
                RootCapability.LOCAL_INTERFACE_INSPECTION,
            ),
            manager.getCapabilities(),
        )
    }

    private class FakeRootCommandRunner : RootCommandRunner {
        var result = RootCommandResult(1, null)
        val results = mutableListOf<RootCommandResult>()
        val commands = mutableListOf<String>()

        override suspend fun run(command: String, timeoutMs: Long): RootCommandResult {
            commands += command
            return if (results.isNotEmpty()) results.removeAt(0) else result
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
