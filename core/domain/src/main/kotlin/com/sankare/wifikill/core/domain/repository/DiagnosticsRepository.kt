package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.domain.model.DiagnosticExecutionEvent
import com.sankare.wifikill.core.domain.model.DiagnosticRequest
import com.sankare.wifikill.core.domain.model.DiagnosticResult
import kotlinx.coroutines.flow.Flow

interface DiagnosticsRepository {
    fun execute(request: DiagnosticRequest): Flow<DiagnosticExecutionEvent>

    fun observeHistory(networkId: String): Flow<List<DiagnosticResult>>
}
