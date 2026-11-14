package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.ObservationSource
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class DefaultDiscoveryEngine @Inject constructor(
    private val availableSources: Set<@JvmSuppressWildcards DiscoverySource>,
) : DiscoveryEngine {

    override fun discover(
        context: NetworkContext,
        sources: Set<ObservationSource>,
        timeoutMs: Long,
    ): Flow<DiscoveryEvent> = flow {
        val selected = availableSources
            .filter { sources.isEmpty() || it.source in sources }
            .sortedBy { it.source.ordinal }

        for (source in selected) {
            emit(DiscoveryEvent.SourceStarted(source.source))

            val isAvailable = try {
                source.isAvailable(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(DiscoveryEvent.SourceFailed(source.source, mapError(e)))
                continue
            }

            if (!isAvailable) {
                emit(DiscoveryEvent.SourceUnavailable(source.source))
                continue
            }

            var found = 0
            try {
                withTimeout(timeoutMs) {
                    source.discover(context).collect { host ->
                        found++
                        emit(DiscoveryEvent.HostFound(source.source, host))
                    }
                }
                emit(DiscoveryEvent.SourceCompleted(source.source, found))
            } catch (e: TimeoutCancellationException) {
                emit(
                    DiscoveryEvent.SourceFailed(
                        source.source,
                        AppError(AppErrorCode.SCAN_TIMEOUT, "Discovery source timed out"),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(DiscoveryEvent.SourceFailed(source.source, mapError(e)))
            }
        }
    }

    private fun mapError(error: Throwable): AppError = when (error) {
        is SecurityException -> AppError(
            AppErrorCode.PERMISSION_DENIED,
            error.message,
        )

        is SocketTimeoutException -> AppError(
            AppErrorCode.SCAN_TIMEOUT,
            error.message,
            retryable = true,
        )

        is IOException -> AppError(
            AppErrorCode.NETWORK_UNAVAILABLE,
            error.message,
            retryable = true,
        )

        else -> AppError(AppErrorCode.UNKNOWN, error.message)
    }
}
