package com.sankare.wifikill.feature.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.usecase.GetDeviceDetailsUseCase
import com.sankare.wifikill.core.domain.usecase.GetDeviceHistoryUseCase
import com.sankare.wifikill.core.domain.usecase.RenameDeviceUseCase
import com.sankare.wifikill.core.domain.usecase.SetDeviceTrustUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceDetailUiState(
    val device: Device? = null,
    val history: List<DeviceObservation> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getDeviceDetails: GetDeviceDetailsUseCase,
    getDeviceHistory: GetDeviceHistoryUseCase,
    private val renameDevice: RenameDeviceUseCase,
    private val setDeviceTrust: SetDeviceTrustUseCase,
    private val strings: StringProvider,
) : ViewModel() {

    private val deviceId: String = checkNotNull(savedStateHandle[DEVICE_ID_ARG])

    val state: StateFlow<DeviceDetailUiState> = combine(
        getDeviceDetails(deviceId),
        getDeviceHistory(deviceId),
    ) { device, history ->
        DeviceDetailUiState(device = device, history = history, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DeviceDetailUiState(),
    )

    private val mutableError = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = mutableError.asStateFlow()

    fun rename(name: String?) {
        viewModelScope.launch {
            handleResult(renameDevice(deviceId, name))
        }
    }

    fun setTrusted(isTrusted: Boolean) {
        viewModelScope.launch {
            handleResult(setDeviceTrust(deviceId, isTrusted))
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    private fun handleResult(result: AppResult<Unit>) {
        if (result is AppResult.Failure) {
            mutableError.value = result.error.message ?: strings.get(R.string.devices_vm_error)
        }
    }

    companion object {
        const val DEVICE_ID_ARG = "deviceId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
