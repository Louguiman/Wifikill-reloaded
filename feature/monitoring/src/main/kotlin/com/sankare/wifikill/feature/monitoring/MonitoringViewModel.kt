package com.sankare.wifikill.feature.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.TrafficSample
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveTrafficUseCase
import com.sankare.wifikill.core.domain.usecase.RefreshTrafficUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val network: NetworkContext? = null,
    val samples: List<TrafficSample> = emptyList(),
    val isMonitoring: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val unavailableReason: String? = null,
    val refreshIntervalSeconds: Int = AppSettings.DEFAULT_MONITORING_INTERVAL_SECONDS,
    val isLoading: Boolean = true,
) {
    val latestSample: TrafficSample? get() = samples.firstOrNull()
}

private data class ControlState(
    val isMonitoring: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val unavailableReason: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonitoringViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeTraffic: ObserveTrafficUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val refreshTraffic: RefreshTrafficUseCase,
    private val strings: StringProvider,
) : ViewModel() {

    private val control = MutableStateFlow(ControlState())
    private var monitoringJob: Job? = null
    private var refreshJob: Job? = null

    val state: StateFlow<MonitoringUiState> = observeCurrentNetwork()
        .flatMapLatest { network ->
            val samples = network?.networkId?.let { observeTraffic(it) }
                ?: flowOf(emptyList())
            combine(samples, control, observeSettings()) { trafficSamples, currentControl, settings ->
                MonitoringUiState(
                    network = network,
                    samples = trafficSamples,
                    isMonitoring = currentControl.isMonitoring,
                    isRefreshing = currentControl.isRefreshing,
                    error = currentControl.error,
                    unavailableReason = currentControl.unavailableReason,
                    refreshIntervalSeconds = settings.monitoringRefreshIntervalSeconds,
                    isLoading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MonitoringUiState(),
        )

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        control.update { it.copy(isMonitoring = true, error = null) }
        monitoringJob = viewModelScope.launch {
            try {
                while (isActive) {
                    refreshOnce()
                    delay(state.value.refreshIntervalSeconds * 1_000L)
                }
            } finally {
                control.update { it.copy(isMonitoring = false, isRefreshing = false) }
            }
        }
    }

    fun pauseMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        refreshJob?.cancel()
        refreshJob = null
        control.update {
            it.copy(isMonitoring = false, isRefreshing = false)
        }
    }

    fun refreshNow() {
        if (control.value.isRefreshing) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshOnce()
        }
    }

    fun clearUnavailable() {
        control.update { it.copy(unavailableReason = null) }
    }

    override fun onCleared() {
        monitoringJob?.cancel()
        refreshJob?.cancel()
        super.onCleared()
    }

    private suspend fun refreshOnce() {
        val networkId = state.value.network?.networkId ?: return
        control.update { it.copy(isRefreshing = true) }
        try {
            when (val result = refreshTraffic(networkId)) {
                is AppResult.Success -> control.update {
                    it.copy(error = null, unavailableReason = null)
                }

                is AppResult.Failure -> control.update {
                    if (result.error.code == AppErrorCode.UNSUPPORTED_CAPABILITY) {
                        it.copy(
                            error = null,
                            unavailableReason = strings.get(R.string.monitoring_vm_unavailable),
                        )
                    } else if (result.error.code == AppErrorCode.INVALID_NETWORK_CONTEXT) {
                        it.copy(error = null, unavailableReason = null)
                    } else {
                        it.copy(
                            error = strings.get(R.string.monitoring_vm_error),
                            unavailableReason = null,
                        )
                    }
                }
            }
        } finally {
            control.update { it.copy(isRefreshing = false) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
