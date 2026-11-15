package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AddRouterRequest
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterInfo
import com.sankare.wifikill.core.domain.repository.RouterRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveRoutersUseCase @Inject constructor(
    private val repository: RouterRepository,
) {
    operator fun invoke(networkId: String): Flow<List<Router>> = repository.observeRouters(networkId)
}

class AddRouterUseCase @Inject constructor(
    private val repository: RouterRepository,
) {
    suspend operator fun invoke(request: AddRouterRequest): AppResult<Router> {
        if (request.networkId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        if (request.name.isBlank() || request.host.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Name and host are required"))
        }
        if (request.port !in 1..65535) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Invalid port"))
        }
        if (request.username.isBlank() || request.password.isEmpty()) {
            return AppResult.Failure(AppError(AppErrorCode.AUTHENTICATION_FAILED, "Credentials are required"))
        }
        return repository.addRouter(request)
    }
}

class ConnectRouterUseCase @Inject constructor(
    private val repository: RouterRepository,
) {
    suspend operator fun invoke(routerId: String): AppResult<RouterInfo> {
        if (routerId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.connectRouter(routerId)
    }
}

class DisconnectRouterUseCase @Inject constructor(
    private val repository: RouterRepository,
) {
    suspend operator fun invoke(routerId: String): AppResult<Unit> = repository.disconnectRouter(routerId)
}

class GetRouterCapabilitiesUseCase @Inject constructor(
    private val repository: RouterRepository,
) {
    suspend operator fun invoke(routerId: String): Set<RouterCapability> =
        repository.getCapabilities(routerId)
}

class RemoveRouterUseCase @Inject constructor(
    private val repository: RouterRepository,
) {
    suspend operator fun invoke(routerId: String): AppResult<Unit> = repository.removeRouter(routerId)
}
