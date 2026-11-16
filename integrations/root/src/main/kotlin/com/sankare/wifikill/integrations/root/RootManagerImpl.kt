package com.sankare.wifikill.integrations.root

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.domain.model.RootCapability
import com.sankare.wifikill.core.domain.model.RootOperation
import com.sankare.wifikill.core.domain.model.RootOperationResult
import com.sankare.wifikill.core.domain.service.RootManager

class RootManagerImpl(
    private val commandRunner: RootCommandRunner,
    private val timeProvider: TimeProvider,
) : RootManager {

    override suspend fun isAvailable(): Boolean {
        val result = commandRunner.run(AVAILABILITY_COMMAND, AVAILABILITY_TIMEOUT_MS)
        return !result.timedOut &&
            result.exitCode == 0 &&
            result.output?.lineSequence()?.any { it.startsWith(ROOT_UID_PREFIX) } == true
    }

    override suspend fun getCapabilities(): Set<RootCapability> =
        if (isAvailable()) SUPPORTED_CAPABILITIES else emptySet()

    override suspend fun execute(
        operation: RootOperation,
    ): AppResult<RootOperationResult> {
        val command = APPROVED_COMMANDS[operation]
            ?: return AppResult.Failure(
                AppError(
                    AppErrorCode.INVALID_ARGUMENT,
                    "Unsupported root operation",
                ),
            )

        if (!isAvailable()) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.ROOT_UNAVAILABLE,
                    "Root access is not available on this device",
                ),
            )
        }

        val result = commandRunner.run(command, OPERATION_TIMEOUT_MS)
        if (result.timedOut) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.ROOT_OPERATION_FAILED,
                    "Root operation timed out",
                    retryable = true,
                ),
            )
        }
        if (result.exitCode != 0) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.ROOT_OPERATION_FAILED,
                    "Root operation failed with exit code ${result.exitCode}",
                    retryable = true,
                ),
            )
        }

        return AppResult.Success(
            RootOperationResult(
                operation = operation,
                output = result.output
                    ?.trim()
                    ?.take(MAX_RESULT_CHARS)
                    ?.takeIf { it.isNotEmpty() },
                executedAt = timeProvider.nowMillis(),
            ),
        )
    }

    companion object {
        const val AVAILABILITY_TIMEOUT_MS = 2_000L
        const val OPERATION_TIMEOUT_MS = 5_000L
        const val MAX_RESULT_CHARS = 8_192
        const val ROOT_UID_PREFIX = "uid=0"

        val SUPPORTED_CAPABILITIES: Set<RootCapability> = setOf(
            RootCapability.ADVANCED_NETWORK_DIAGNOSTICS,
            RootCapability.LOCAL_INTERFACE_INSPECTION,
        )

        private const val AVAILABILITY_COMMAND = "id"
        private const val INSPECT_INTERFACE_COMMAND = "ip addr show"
        private const val READ_CONFIGURATION_COMMAND = "ip route show"

        private val APPROVED_COMMANDS: Map<RootOperation, String> = mapOf(
            RootOperation.INSPECT_LOCAL_INTERFACE to INSPECT_INTERFACE_COMMAND,
            RootOperation.READ_NETWORK_CONFIGURATION to READ_CONFIGURATION_COMMAND,
        )
    }
}
