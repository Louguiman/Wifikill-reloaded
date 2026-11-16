package com.sankare.wifikill.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.model.RetentionKind
import com.sankare.wifikill.core.domain.usecase.DeleteLocalDataUseCase
import com.sankare.wifikill.core.domain.usecase.GetRootAvailabilityUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.core.domain.usecase.SetRootModeEnabledUseCase
import com.sankare.wifikill.core.domain.usecase.UpdateMonitoringIntervalUseCase
import com.sankare.wifikill.core.domain.usecase.UpdateRetentionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val rootAvailable: Boolean? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

private data class FeedbackState(
    val rootAvailable: Boolean? = null,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val checkRootAvailability: GetRootAvailabilityUseCase,
    private val setRootMode: SetRootModeEnabledUseCase,
    private val updateMonitoringInterval: UpdateMonitoringIntervalUseCase,
    private val updateRetention: UpdateRetentionUseCase,
    private val deleteLocalDataUseCase: DeleteLocalDataUseCase,
) : ViewModel() {

    private val feedback = MutableStateFlow(FeedbackState())

    val state: StateFlow<SettingsUiState> = combine(
        observeSettings(),
        feedback,
    ) { settings, currentFeedback ->
        SettingsUiState(
            settings = settings,
            rootAvailable = currentFeedback.rootAvailable,
            isLoading = false,
            isDeleting = currentFeedback.isDeleting,
            message = currentFeedback.message,
            error = currentFeedback.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    init {
        refreshRootAvailability()
    }

    fun refreshRootAvailability() {
        viewModelScope.launch {
            val available = checkRootAvailability()
            feedback.update { it.copy(rootAvailable = available) }
        }
    }

    fun setMonitoringInterval(seconds: Int) {
        viewModelScope.launch {
            updateMonitoringInterval(seconds).report()
        }
    }

    fun setRetention(kind: RetentionKind, days: Int) {
        viewModelScope.launch {
            updateRetention(kind, days).report()
        }
    }

    fun setRootModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && feedback.value.rootAvailable != true) {
                feedback.update {
                    it.copy(error = "ROOT_UNAVAILABLE")
                }
                return@launch
            }
            setRootMode(enabled).report()
        }
    }

    fun deleteLocalData() {
        if (feedback.value.isDeleting) return
        viewModelScope.launch {
            feedback.update { it.copy(isDeleting = true, error = null, message = null) }
            try {
                when (val result = deleteLocalDataUseCase()) {
                    is AppResult.Success -> feedback.update {
                        it.copy(
                            isDeleting = false,
                            message = "LOCAL_DATA_DELETED",
                        )
                    }

                    is AppResult.Failure -> feedback.update {
                        it.copy(
                            isDeleting = false,
                            error = result.error.message ?: result.error.code.name,
                        )
                    }
                }
            } finally {
                feedback.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun clearFeedback() {
        feedback.update { it.copy(message = null, error = null) }
    }

    private fun AppResult<Unit>.report() {
        val failure = this as? AppResult.Failure ?: return
        feedback.update {
            it.copy(
                message = null,
                error = failure.error.message ?: failure.error.code.name,
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
