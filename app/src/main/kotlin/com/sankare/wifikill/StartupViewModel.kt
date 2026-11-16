package com.sankare.wifikill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankare.wifikill.core.domain.usecase.CleanupExpiredDataUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val cleanupExpiredData: CleanupExpiredDataUseCase,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = observeSettings().first()
            _startDestination.value = if (settings.onboardingCompleted) {
                Destinations.DASHBOARD
            } else {
                Destinations.ONBOARDING
            }
        }
        viewModelScope.launch {
            cleanupExpiredData()
        }
    }
}
