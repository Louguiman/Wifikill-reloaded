package com.sankare.wifikill.feature.accessmanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.usecase.ApplyAccessPolicyUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveAccessPoliciesUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveDevicesUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveRoutersUseCase
import com.sankare.wifikill.core.domain.usecase.RemoveAccessPolicyUseCase
import com.sankare.wifikill.core.domain.usecase.VerifyAccessPolicyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccessManagementUiState(
    val network: NetworkContext? = null,
    val routers: List<Router> = emptyList(),
    val devices: List<Device> = emptyList(),
    val selectedRouterId: String? = null,
    val selectedDeviceId: String? = null,
    val policies: List<AccessPolicy> = emptyList(),
    val pendingAction: AccessPolicyAction = AccessPolicyAction.BLOCK_INTERNET,
    val reason: String = "",
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val busyPolicyId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

private data class Selection(
    val routerId: String? = null,
    val deviceId: String? = null,
)

private data class ActionState(
    val isApplying: Boolean = false,
    val busyPolicyId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

private data class BaseState(
    val network: NetworkContext?,
    val routers: List<Router>,
    val devices: List<Device>,
    val selection: Selection,
    val action: ActionState,
) {
    val routerId: String? = selection.routerId?.takeIf { id -> routers.any { it.id == id } }
        ?: routers.firstOrNull()?.id

    val deviceId: String? = selection.deviceId?.takeIf { id -> devices.any { it.id == id } }
        ?: devices.firstOrNull()?.id
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccessManagementViewModel @Inject constructor(
    observeCurrentNetwork: ObserveCurrentNetworkUseCase,
    observeRouters: ObserveRoutersUseCase,
    observeDevices: ObserveDevicesUseCase,
    private val observePolicies: ObserveAccessPoliciesUseCase,
    private val applyPolicy: ApplyAccessPolicyUseCase,
    private val removePolicy: RemoveAccessPolicyUseCase,
    private val verifyPolicy: VerifyAccessPolicyUseCase,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())
    private val actions = MutableStateFlow(ActionState())
    private val pendingAction = MutableStateFlow(AccessPolicyAction.BLOCK_INTERNET)
    private val reason = MutableStateFlow("")

    val state: StateFlow<AccessManagementUiState> = observeCurrentNetwork()
        .flatMapLatest { network ->
            val networkId = network?.networkId
            val routers = if (networkId == null) {
                flowOf(emptyList())
            } else {
                observeRouters(networkId)
            }
            val devices = if (networkId == null) {
                flowOf(emptyList())
            } else {
                observeDevices(networkId)
            }
            combine(
                routers,
                devices,
                selection,
                actions,
            ) { routerList: List<Router>, deviceList: List<Device>, sel: Selection, act: ActionState ->
                BaseState(network, routerList, deviceList, sel, act)
            }.flatMapLatest { base ->
                    val policies: Flow<List<AccessPolicy>> = base.routerId?.let {
                        observePolicies(it)
                    } ?: flowOf(emptyList())
                    combine(
                        policies,
                        pendingAction,
                        reason,
                    ) { policyList: List<AccessPolicy>, action: AccessPolicyAction, text: String ->
                        AccessManagementUiState(
                            network = base.network,
                            routers = base.routers,
                            devices = base.devices,
                            selectedRouterId = base.routerId,
                            selectedDeviceId = base.deviceId,
                            policies = policyList,
                            pendingAction = action,
                            reason = text,
                            isLoading = false,
                            isApplying = base.action.isApplying,
                            busyPolicyId = base.action.busyPolicyId,
                            message = base.action.message,
                            error = base.action.error,
                        )
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AccessManagementUiState(),
        )

    fun selectRouter(routerId: String) {
        selection.update { it.copy(routerId = routerId, deviceId = null) }
    }

    fun selectDevice(deviceId: String) {
        selection.update { it.copy(deviceId = deviceId) }
    }

    fun setAction(action: AccessPolicyAction) {
        pendingAction.value = action
    }

    fun updateReason(value: String) {
        reason.value = value.take(MAX_REASON_LENGTH)
    }

    fun apply() {
        val current = state.value
        val routerId = current.selectedRouterId
        val deviceId = current.selectedDeviceId
        if (routerId == null) {
            actions.update { it.copy(error = "Add and connect a router first") }
            return
        }
        if (deviceId == null) {
            actions.update { it.copy(error = "Select a device") }
            return
        }
        viewModelScope.launch {
            actions.update { it.copy(isApplying = true, message = null, error = null) }
            val result = applyPolicy(
                AccessPolicyRequest(
                    routerId = routerId,
                    deviceId = deviceId,
                    action = pendingAction.value,
                    reason = reason.value,
                    expiresAt = null,
                ),
            )
            actions.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        isApplying = false,
                        message = "Policy ${result.data.status.name.lowercase()} after verification",
                    )

                    is AppResult.Failure -> it.copy(
                        isApplying = false,
                        error = result.error.message ?: result.error.code.name,
                    )
                }
            }
        }
    }

    fun remove(policyId: String) {
        launchPolicyAction(policyId) {
            when (val result = removePolicy(policyId)) {
                is AppResult.Success -> AppResult.Success("Policy removed after verification")
                is AppResult.Failure -> result
            }
        }
    }

    fun verify(policyId: String) {
        launchPolicyAction(policyId) {
            when (val result = verifyPolicy(policyId)) {
                is AppResult.Success -> AppResult.Success(
                    if (result.data.isVerified) {
                        "Router confirms the current policy state"
                    } else {
                        "Router did not confirm the policy state"
                    },
                )

                is AppResult.Failure -> result
            }
        }
    }

    fun clearFeedback() {
        actions.update { it.copy(message = null, error = null) }
    }

    private fun launchPolicyAction(
        policyId: String,
        block: suspend () -> AppResult<String>,
    ) {
        viewModelScope.launch {
            actions.update {
                it.copy(busyPolicyId = policyId, message = null, error = null)
            }
            when (val result = block()) {
                is AppResult.Success -> actions.update {
                    it.copy(busyPolicyId = null, message = result.data)
                }

                is AppResult.Failure -> actions.update {
                    it.copy(
                        busyPolicyId = null,
                        error = result.error.message ?: result.error.code.name,
                    )
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_REASON_LENGTH = 256
    }
}
