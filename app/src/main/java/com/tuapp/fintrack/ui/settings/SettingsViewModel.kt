package com.tuapp.fintrack.ui.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: FinTrackRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val requireCategory: StateFlow<Boolean> = settingsRepository.requireCategory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val smsAutoEnabled: StateFlow<Boolean> = settingsRepository.smsAutoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val smsIdentifierText: StateFlow<String> = settingsRepository.smsIdentifierText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val smsAmountPrefix: StateFlow<String> = settingsRepository.smsAmountPrefix
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val smsTransactionType: StateFlow<String> = settingsRepository.smsTransactionType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "EXPENSE")

    val smsDefaultCategoryId: StateFlow<Long?> = settingsRepository.smsDefaultCategoryId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val smsDescriptionTemplate: StateFlow<String> = settingsRepository.smsDescriptionTemplate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val startingBalanceCents: StateFlow<Long> = settingsRepository.startingBalanceCents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val startingBalanceSetAt: StateFlow<Long> = settingsRepository.startingBalanceSetAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _hasNotificationAccess = MutableStateFlow(checkNotificationAccess())
    val hasNotificationAccess: StateFlow<Boolean> = _hasNotificationAccess.asStateFlow()

    fun refreshNotificationAccess() {
        _hasNotificationAccess.value = checkNotificationAccess()
    }

    private fun checkNotificationAccess(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
    }

    fun setRequireCategory(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRequireCategory(value)
        }
    }

    fun setSmsAutoEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmsAutoEnabled(value)
        }
    }

    fun setSmsIdentifierText(value: String) {
        viewModelScope.launch {
            settingsRepository.setSmsIdentifierText(value)
        }
    }

    fun setSmsAmountPrefix(value: String) {
        viewModelScope.launch {
            settingsRepository.setSmsAmountPrefix(value)
        }
    }

    fun setSmsTransactionType(value: String) {
        viewModelScope.launch {
            settingsRepository.setSmsTransactionType(value)
        }
    }

    fun setSmsDefaultCategoryId(value: Long?) {
        viewModelScope.launch {
            settingsRepository.setSmsDefaultCategoryId(value)
        }
    }

    fun setSmsDescriptionTemplate(value: String) {
        viewModelScope.launch {
            settingsRepository.setSmsDescriptionTemplate(value)
        }
    }

    fun setStartingBalance(cents: Long) {
        viewModelScope.launch {
            settingsRepository.setStartingBalance(cents)
        }
    }
}
