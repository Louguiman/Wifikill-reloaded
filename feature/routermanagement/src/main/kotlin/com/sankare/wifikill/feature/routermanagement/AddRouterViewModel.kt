package com.sankare.wifikill.feature.routermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AddRouterRequest
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.usecase.AddRouterUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddRouterForm(
    val name: String = "",
    val host: String = "",
    val port: String = "443",
    val protocol: RouterProtocol = RouterProtocol.HTTPS,
    val username: String = "",
    val password: String = "",
)

data class AddRouterUiState(
    val network: NetworkContext? = null,
    val form: AddRouterForm = AddRouterForm(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class AddRouterViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    private val addRouter: AddRouterUseCase,
) : ViewModel() {

    private val form = MutableStateFlow(AddRouterForm())
    private val status = MutableStateFlow(SaveStatus())

    val state: StateFlow<AddRouterUiState> = combine(
        observeCurrentNetwork(),
        form,
        status,
    ) { network, form, status ->
        AddRouterUiState(
            network = network,
            form = form,
            isSaving = status.isSaving,
            error = status.error,
            isSaved = status.isSaved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AddRouterUiState(),
    )

    fun updateName(value: String) = form.update { it.copy(name = value) }

    fun updateHost(value: String) = form.update { it.copy(host = value) }

    fun updatePort(value: String) = form.update { it.copy(port = value.filter(Char::isDigit)) }

    fun updateProtocol(value: RouterProtocol) = form.update { it.copy(protocol = value) }

    fun updateUsername(value: String) = form.update { it.copy(username = value) }

    fun updatePassword(value: String) = form.update { it.copy(password = value) }

    fun save() {
        if (status.value.isSaving) return
        val networkId = state.value.network?.networkId
        if (networkId == null) {
            status.update { it.copy(error = "Connect to a network before adding a router") }
            return
        }
        val current = form.value
        val port = current.port.toIntOrNull()
        if (port == null) {
            status.update { it.copy(error = "Enter a valid port") }
            return
        }

        viewModelScope.launch {
            status.update { it.copy(isSaving = true, error = null) }
            val result = addRouter(
                AddRouterRequest(
                    networkId = networkId,
                    name = current.name,
                    host = current.host.trim(),
                    port = port,
                    protocol = current.protocol,
                    adapterType = RouterAdapterType.GENERIC,
                    username = current.username,
                    password = current.password,
                ),
            )
            status.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSaving = false, isSaved = true)
                    is AppResult.Failure -> it.copy(
                        isSaving = false,
                        error = result.error.message ?: result.error.code.name,
                    )
                }
            }
        }
    }

    private data class SaveStatus(
        val isSaving: Boolean = false,
        val error: String? = null,
        val isSaved: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
