package com.sankare.wifikill.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isCompleting: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun complete() {
        if (_state.value.isCompleting || _state.value.completed) return
        viewModelScope.launch {
            _state.update { it.copy(isCompleting = true, error = null) }
            when (val result = completeOnboarding()) {
                is AppResult.Success -> _state.update {
                    it.copy(isCompleting = false, completed = true)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(
                        isCompleting = false,
                        error = result.error.message ?: result.error.code.name,
                    )
                }
            }
        }
    }
}
