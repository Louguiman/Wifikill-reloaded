package com.sankare.wifikill.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.ObservationSource
import com.sankare.wifikill.core.domain.model.ScanEvent
import com.sankare.wifikill.core.domain.model.ScanRequest
import com.sankare.wifikill.core.domain.usecase.CancelNetworkScanUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveDevicesUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveScanEventsUseCase
import com.sankare.wifikill.core.domain.usecase.StartNetworkScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val network: NetworkContext? = null,
    val devices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val sessionId: String? = null,
    val devicesFound: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeDevices: ObserveDevicesUseCase,
    private val startNetworkScan: StartNetworkScanUseCase,
    private val cancelNetworkScan: CancelNetworkScanUseCase,
    private val observeScanEvents: ObserveScanEventsUseCase,
    private val strings: StringProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = mutableState.asStateFlow()

    private var eventsJob: Job? = null

    init {
        viewModelScope.launch {
            observeCurrentNetwork().collect { network ->
                mutableState.update { it.copy(network = network) }
            }
        }

        viewModelScope.launch {
            observeCurrentNetwork()
                .map { it?.networkId }
                .distinctUntilChanged()
                .flatMapLatest { networkId ->
                    if (networkId == null) flowOf(emptyList()) else observeDevices(networkId)
                }
                .collect { devices ->
                    mutableState.update { it.copy(devices = devices) }
                }
        }
    }

    fun startScan() {
        val networkId = mutableState.value.network?.networkId ?: return
        if (mutableState.value.isScanning) return

        mutableState.update {
            it.copy(
                isScanning = true,
                devicesFound = 0,
                errorMessage = null,
                statusMessage = strings.get(R.string.discovery_vm_starting),
            )
        }

        viewModelScope.launch {
            val result = startNetworkScan(
                ScanRequest(
                    networkId = networkId,
                    sources = setOf(ObservationSource.LOCAL_DISCOVERY),
                    timeoutMs = SCAN_TIMEOUT_MS,
                ),
            )
            when (result) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(sessionId = result.data) }
                    observeEvents(result.data)
                }

                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            isScanning = false,
                            sessionId = null,
                            errorMessage = result.error.message ?: result.error.code.name,
                        )
                    }
                }
            }
        }
    }

    fun cancelScan() {
        val sessionId = mutableState.value.sessionId ?: return
        viewModelScope.launch {
            cancelNetworkScan(sessionId)
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun observeEvents(sessionId: String) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            observeScanEvents(sessionId).collect { event ->
                when (event) {
                    is ScanEvent.Started -> mutableState.update {
                        it.copy(
                            isScanning = true,
                            statusMessage = strings.get(R.string.discovery_vm_scanning_network),
                        )
                    }

                    is ScanEvent.SourceStarted -> mutableState.update {
                        it.copy(
                            statusMessage = strings.get(
                                R.string.discovery_vm_scanning_source,
                                strings.get(event.source.labelRes()),
                            ),
                        )
                    }

                    is ScanEvent.SourceCompleted -> mutableState.update {
                        it.copy(
                            statusMessage = strings.get(
                                R.string.discovery_vm_source_completed,
                                strings.get(event.source.labelRes()),
                                event.devicesFound,
                            ),
                        )
                    }

                    is ScanEvent.DeviceDiscovered -> mutableState.update {
                        val found = it.devicesFound + 1
                        it.copy(
                            devicesFound = found,
                            statusMessage = strings.get(R.string.discovery_vm_found, found),
                        )
                    }

                    is ScanEvent.Progress -> Unit

                    is ScanEvent.Completed -> mutableState.update {
                        it.copy(
                            isScanning = false,
                            sessionId = null,
                            devicesFound = event.devicesFound,
                            statusMessage = strings.get(
                                R.string.discovery_vm_complete,
                                event.devicesFound,
                            ),
                        )
                    }

                    is ScanEvent.Failed -> mutableState.update {
                        it.copy(
                            isScanning = false,
                            sessionId = null,
                            errorMessage = event.error.message ?: event.error.code.name,
                            statusMessage = null,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        eventsJob?.cancel()
        super.onCleared()
    }

    private fun ObservationSource.labelRes(): Int = when (this) {
        ObservationSource.ANDROID_NETWORK -> R.string.discovery_source_android
        ObservationSource.LOCAL_DISCOVERY -> R.string.discovery_source_local
        ObservationSource.ROUTER_INVENTORY -> R.string.discovery_source_router
        ObservationSource.MANUAL -> R.string.discovery_source_manual
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 30_000L
    }
}
