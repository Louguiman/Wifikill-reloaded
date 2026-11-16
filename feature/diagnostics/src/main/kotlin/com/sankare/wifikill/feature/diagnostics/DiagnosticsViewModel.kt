package com.sankare.wifikill.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.domain.model.DiagnosticExecutionEvent
import com.sankare.wifikill.core.domain.model.DiagnosticRequest
import com.sankare.wifikill.core.domain.model.DiagnosticResult
import com.sankare.wifikill.core.domain.model.DiagnosticType
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.RootOperation
import com.sankare.wifikill.core.domain.model.RootOperationResult
import com.sankare.wifikill.core.domain.usecase.ExecuteRootOperationUseCase
import com.sankare.wifikill.core.domain.usecase.GetRootAvailabilityUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveDiagnosticHistoryUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.core.domain.usecase.RunDiagnosticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val network: NetworkContext? = null,
    val history: List<DiagnosticResult> = emptyList(),
    val latestResult: DiagnosticResult? = null,
    val runningType: DiagnosticType? = null,
    val isRunningAll: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val rootModeEnabled: Boolean = false,
    val rootAvailable: Boolean? = null,
    val rootBusy: Boolean = false,
    val rootResult: RootOperationResult? = null,
    val rootError: String? = null,
    val isLoading: Boolean = true,
) {
    val isBusy: Boolean get() = runningType != null || isRunningAll
}

private data class ActionState(
    val latestResult: DiagnosticResult? = null,
    val runningType: DiagnosticType? = null,
    val isRunningAll: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val rootAvailable: Boolean? = null,
    val rootBusy: Boolean = false,
    val rootResult: RootOperationResult? = null,
    val rootError: String? = null,
)

internal val DIAGNOSTIC_TYPES = listOf(
    DiagnosticType.GATEWAY_REACHABILITY,
    DiagnosticType.DNS_RESOLUTION,
    DiagnosticType.INTERNET_CONNECTIVITY,
    DiagnosticType.LATENCY,
    DiagnosticType.PACKET_LOSS,
    DiagnosticType.NETWORK_CONFIGURATION,
)

internal fun DiagnosticType.labelRes(): Int = when (this) {
    DiagnosticType.GATEWAY_REACHABILITY -> R.string.diagnostics_type_gateway
    DiagnosticType.DNS_RESOLUTION -> R.string.diagnostics_type_dns
    DiagnosticType.INTERNET_CONNECTIVITY -> R.string.diagnostics_type_internet
    DiagnosticType.LATENCY -> R.string.diagnostics_type_latency
    DiagnosticType.PACKET_LOSS -> R.string.diagnostics_type_packet_loss
    DiagnosticType.NETWORK_CONFIGURATION -> R.string.diagnostics_type_network_configuration
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeHistory: ObserveDiagnosticHistoryUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val checkRootAvailability: GetRootAvailabilityUseCase,
    private val executeRootOperationUseCase: ExecuteRootOperationUseCase,
    private val runDiagnostics: RunDiagnosticsUseCase,
    private val strings: StringProvider,
) : ViewModel() {

    private val action = MutableStateFlow(ActionState())

    init {
        viewModelScope.launch {
            val available = checkRootAvailability()
            action.update { it.copy(rootAvailable = available) }
        }
    }

    val state: StateFlow<DiagnosticsUiState> = observeCurrentNetwork()
        .flatMapLatest { network ->
            val history = network?.networkId?.let { observeHistory(it) }
                ?: flowOf(emptyList())
            combine(history, action, observeSettings()) { results, currentAction, settings ->
                DiagnosticsUiState(
                    network = network,
                    history = results,
                    latestResult = currentAction.latestResult,
                    runningType = currentAction.runningType,
                    isRunningAll = currentAction.isRunningAll,
                    progress = currentAction.progress,
                    error = currentAction.error,
                    rootModeEnabled = settings.rootModeEnabled,
                    rootAvailable = currentAction.rootAvailable,
                    rootBusy = currentAction.rootBusy,
                    rootResult = currentAction.rootResult,
                    rootError = currentAction.rootError,
                    isLoading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DiagnosticsUiState(),
        )

    fun runDiagnostic(type: DiagnosticType) {
        if (state.value.isBusy) return
        viewModelScope.launch {
            execute(type)
        }
    }

    fun runAllDiagnostics() {
        if (state.value.isBusy) return
        viewModelScope.launch {
            action.update { it.copy(isRunningAll = true, error = null) }
            try {
                DIAGNOSTIC_TYPES.forEach { type -> execute(type) }
            } finally {
                action.update {
                    it.copy(isRunningAll = false, runningType = null, progress = null)
                }
            }
        }
    }

    fun clearError() {
        action.update { it.copy(error = null) }
    }

    fun executeRootOperation(operation: RootOperation) {
        if (action.value.rootBusy) return
        viewModelScope.launch {
            action.update {
                it.copy(rootBusy = true, rootError = null, rootResult = null)
            }
            try {
                when (val result = executeRootOperationUseCase(operation)) {
                    is AppResult.Success -> action.update {
                        it.copy(rootBusy = false, rootResult = result.data, rootError = null)
                    }

                    is AppResult.Failure -> action.update {
                        it.copy(
                            rootBusy = false,
                            rootResult = null,
                            rootError = result.error.message ?: result.error.code.name,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                action.update {
                    it.copy(
                        rootBusy = false,
                        rootError = e.message ?: strings.get(R.string.diagnostics_vm_root_failed),
                    )
                }
            } finally {
                action.update { it.copy(rootBusy = false) }
            }
        }
    }

    fun clearRootResult() {
        action.update { it.copy(rootResult = null, rootError = null) }
    }

    private suspend fun execute(type: DiagnosticType) {
        val networkId = state.value.network?.networkId
        if (networkId == null) {
            action.update {
                it.copy(error = strings.get(R.string.diagnostics_vm_no_network))
            }
            return
        }

        action.update {
            it.copy(runningType = type, progress = null, error = null)
        }
        try {
            runDiagnostics(
                DiagnosticRequest(
                    networkId = networkId,
                    type = type,
                    target = null,
                ),
            ).collect { event ->
                when (event) {
                    DiagnosticExecutionEvent.Started -> action.update {
                        it.copy(
                            progress = strings.get(
                                R.string.diagnostics_vm_starting,
                                strings.get(type.labelRes()),
                            ),
                        )
                    }

                    is DiagnosticExecutionEvent.Progress -> action.update {
                        it.copy(progress = event.message)
                    }

                    is DiagnosticExecutionEvent.Completed -> action.update {
                        it.copy(
                            latestResult = event.result,
                            runningType = null,
                            progress = null,
                            error = null,
                        )
                    }

                    is DiagnosticExecutionEvent.Failed -> action.update {
                        it.copy(
                            runningType = null,
                            progress = null,
                            error = event.error.message ?: event.error.code.name,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            action.update {
                it.copy(
                    runningType = null,
                    progress = null,
                    error = e.message ?: strings.get(R.string.diagnostics_vm_failed),
                )
            }
        } finally {
            action.update {
                if (it.runningType == type) it.copy(runningType = null) else it
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
