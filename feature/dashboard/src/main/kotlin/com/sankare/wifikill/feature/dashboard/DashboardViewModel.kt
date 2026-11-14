package com.sankare.wifikill.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveDevicesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val network: NetworkContext? = null,
    val deviceCount: Int = 0,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeDevices: ObserveDevicesUseCase,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = observeCurrentNetwork()
        .flatMapLatest { network ->
            val networkId = network?.networkId
            if (networkId == null) {
                flowOf(DashboardUiState(network = network, isLoading = false))
            } else {
                observeDevices(networkId).map { devices ->
                    DashboardUiState(
                        network = network,
                        deviceCount = devices.size,
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DashboardUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
