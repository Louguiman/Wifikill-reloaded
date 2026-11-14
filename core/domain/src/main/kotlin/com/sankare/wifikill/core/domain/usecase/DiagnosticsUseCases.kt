package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.domain.model.DiagnosticExecutionEvent
import com.sankare.wifikill.core.domain.model.DiagnosticRequest
import com.sankare.wifikill.core.domain.repository.DiagnosticsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RunDiagnosticsUseCase @Inject constructor(
    private val repository: DiagnosticsRepository,
) {
    operator fun invoke(request: DiagnosticRequest): Flow<DiagnosticExecutionEvent> {
        if (request.networkId.isBlank()) {
            return flowOf(
                DiagnosticExecutionEvent.Failed(
                    AppError(AppErrorCode.INVALID_NETWORK_CONTEXT),
                ),
            )
        }
        return repository.execute(request)
    }
}
