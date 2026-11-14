package com.sankare.wifikill.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.domain.model.Device
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

data class DevicesUiState(
    val network: NetworkContext? = null,
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevicesViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeDevices: ObserveDevicesUseCase,
) : ViewModel() {

    val state: StateFlow<DevicesUiState> = observeCurrentNetwork()
        .flatMapLatest { network ->
            val networkId = network?.networkId
            if (networkId == null) {
                flowOf(DevicesUiState(network = network, isLoading = false))
            } else {
                observeDevices(networkId).map { devices ->
                    DevicesUiState(network = network, devices = devices, isLoading = false)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DevicesUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
