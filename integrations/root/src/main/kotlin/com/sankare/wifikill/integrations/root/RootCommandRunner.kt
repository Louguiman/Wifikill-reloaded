package com.sankare.wifikill.integrations.root

import com.sankare.wifikill.core.common.DispatchersProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

data class RootCommandResult(
    val exitCode: Int,
    val output: String?,
    val timedOut: Boolean = false,
)

interface RootCommandRunner {
    suspend fun run(command: String, timeoutMs: Long): RootCommandResult
}

class SuRootCommandRunner(
    private val dispatchers: DispatchersProvider,
) : RootCommandRunner {

    override suspend fun run(command: String, timeoutMs: Long): RootCommandResult =
        withContext(dispatchers.io) {
            val process = ProcessBuilder(SU_BINARY, "-c", command)
                .redirectErrorStream(true)
                .start()
            try {
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                }
                val output = process.inputStream.bufferedReader().use { reader ->
                    readLimited(reader)
                }
                RootCommandResult(
                    exitCode = if (finished) process.exitValue() else TIMEOUT_EXIT_CODE,
                    output = output.ifBlank { null },
                    timedOut = !finished,
                )
            } finally {
                process.destroy()
            }
        }

    private fun readLimited(reader: java.io.Reader): String {
        val buffer = CharArray(MAX_OUTPUT_CHARS)
        var total = 0
        while (total < buffer.size) {
            val read = reader.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return String(buffer, 0, total).trim()
    }

    companion object {
        const val TIMEOUT_EXIT_CODE = -1
        const val MAX_OUTPUT_CHARS = 8_192
        private const val SU_BINARY = "su"
    }
}
