package com.tuapp.fintrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val requireCategory: StateFlow<Boolean> = settingsRepository.requireCategory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRequireCategory(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRequireCategory(value)
        }
    }
}
