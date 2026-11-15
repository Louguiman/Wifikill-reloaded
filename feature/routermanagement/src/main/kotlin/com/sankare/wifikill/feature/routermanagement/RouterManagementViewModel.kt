package com.sankare.wifikill.feature.routermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.usecase.ConnectRouterUseCase
import com.sankare.wifikill.core.domain.usecase.DisconnectRouterUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveRoutersUseCase
import com.sankare.wifikill.core.domain.usecase.RemoveRouterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

data class RouterManagementUiState(
    val network: NetworkContext? = null,
    val routers: List<Router> = emptyList(),
    val isLoading: Boolean = true,
    val busyRouterId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

private data class RouterActionState(
    val busyRouterId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RouterManagementViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeRouters: ObserveRoutersUseCase,
    private val connectRouter: ConnectRouterUseCase,
    private val disconnectRouter: DisconnectRouterUseCase,
    private val removeRouter: RemoveRouterUseCase,
) : ViewModel() {

    private val actions = MutableStateFlow(RouterActionState())

    val state: StateFlow<RouterManagementUiState> = observeCurrentNetwork()
        .flatMapLatest { network ->
            val networkId = network?.networkId
            val routersFlow = if (networkId == null) {
                flowOf(emptyList())
            } else {
                observeRouters(networkId)
            }
            combine(routersFlow, actions) { routers, action ->
                RouterManagementUiState(
                    network = network,
                    routers = routers,
                    isLoading = false,
                    busyRouterId = action.busyRouterId,
                    message = action.message,
                    error = action.error,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RouterManagementUiState(),
        )

    fun connect(routerId: String) {
        launchAction(routerId) {
            when (val result = connectRouter(routerId)) {
                is AppResult.Success -> actions.update {
                    it.copy(message = "Connected to ${result.data.managementAddress}")
                }

                is AppResult.Failure -> actions.update {
                    it.copy(error = result.error.message ?: result.error.code.name)
                }
            }
        }
    }

    fun disconnect(routerId: String) {
        launchAction(routerId) {
            when (val result = disconnectRouter(routerId)) {
                is AppResult.Success -> actions.update { it.copy(message = "Disconnected") }
                is AppResult.Failure -> actions.update {
                    it.copy(error = result.error.message ?: result.error.code.name)
                }
            }
        }
    }

    fun forget(routerId: String) {
        launchAction(routerId) {
            when (val result = removeRouter(routerId)) {
                is AppResult.Success -> actions.update { it.copy(message = "Router removed") }
                is AppResult.Failure -> actions.update {
                    it.copy(error = result.error.message ?: result.error.code.name)
                }
            }
        }
    }

    fun clearFeedback() {
        actions.update { it.copy(message = null, error = null) }
    }

    private fun launchAction(routerId: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            actions.update { it.copy(busyRouterId = routerId, message = null, error = null) }
            try {
                block()
            } finally {
                actions.update { it.copy(busyRouterId = null) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
