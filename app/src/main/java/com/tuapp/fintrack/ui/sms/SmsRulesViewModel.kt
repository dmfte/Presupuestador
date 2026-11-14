package com.tuapp.fintrack.ui.sms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import com.tuapp.fintrack.sms.SmsRule
import com.tuapp.fintrack.sms.SmsRuleMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmsRuleRow(val rule: SmsRule, val categoryName: String?, val duplicateOf: SmsRule?)

@HiltViewModel
class SmsRulesViewModel @Inject constructor(
    private val settings: SettingsRepository,
    repository: FinTrackRepository
) : ViewModel() {

    val rows: StateFlow<List<SmsRuleRow>?> = combine(
        settings.smsRules,
        repository.allCategories
    ) { rules, categories ->
        val shadowed = SmsRuleMatcher.shadowedBy(rules)
        val names = categories.associate { it.id to it.name }
        rules.map { SmsRuleRow(it, it.categoryId?.let(names::get), shadowed[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(id: Long, enabled: Boolean) = edit { rules ->
        rules.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    fun move(id: Long, delta: Int) = edit { rules ->
        val from = rules.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in rules.indices) rules
        else rules.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun edit(transform: (List<SmsRule>) -> List<SmsRule>) {
        viewModelScope.launch { settings.updateSmsRules(transform) }
    }
}

data class SmsRuleEditState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val identifierText: String = "",
    val amountPrefix: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: Long? = null,
    val descriptionTemplate: String = "",
    val enabled: Boolean = true,
    val showErrors: Boolean = false,
    val finished: Boolean = false
) {
    val identifierError: Boolean get() = showErrors && identifierText.isBlank()
    val prefixError: Boolean get() = showErrors && amountPrefix.isBlank()
}

@HiltViewModel
class SmsRuleEditViewModel @Inject constructor(
    private val settings: SettingsRepository,
    repository: FinTrackRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ruleId: Long? = savedStateHandle.get<Long>("ruleId")?.takeIf { it > 0L }

    private val _state = MutableStateFlow(SmsRuleEditState(isNew = ruleId == null, loaded = ruleId == null))
    val state: StateFlow<SmsRuleEditState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                val rule = settings.smsRules.first().find { it.id == ruleId }
                _state.update {
                    if (rule == null) it.copy(isNew = true, loaded = true)
                    else it.copy(
                        loaded = true,
                        name = rule.name,
                        identifierText = rule.identifierText,
                        amountPrefix = rule.amountPrefix,
                        type = rule.type,
                        categoryId = rule.categoryId,
                        descriptionTemplate = rule.descriptionTemplate,
                        enabled = rule.enabled
                    )
                }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onIdentifier(v: String) = _state.update { it.copy(identifierText = v) }
    fun onPrefix(v: String) = _state.update { it.copy(amountPrefix = v) }
    fun onType(v: TransactionType) = _state.update { it.copy(type = v) }
    fun onCategory(v: Long?) = _state.update { it.copy(categoryId = v) }
    fun onDescription(v: String) = _state.update { it.copy(descriptionTemplate = v) }
    fun onEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }

    fun save() {
        val s = _state.value
        if (s.identifierText.isBlank() || s.amountPrefix.isBlank()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            settings.updateSmsRules { rules ->
                val existing = ruleId?.let { id -> rules.find { it.id == id } }
                val rule = SmsRule(
                    id = existing?.id ?: ((rules.maxOfOrNull { it.id } ?: 0L) + 1L),
                    name = s.name.trim(),
                    identifierText = s.identifierText.trim(),
                    // Prefix keeps inner/trailing spaces on purpose (e.g. "USD "), only drop leading ones.
                    amountPrefix = s.amountPrefix.trimStart(),
                    type = s.type,
                    categoryId = s.categoryId,
                    descriptionTemplate = s.descriptionTemplate.trim(),
                    enabled = s.enabled
                )
                if (existing != null) rules.map { if (it.id == rule.id) rule else it } else rules + rule
            }
            _state.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val id = ruleId ?: return
        viewModelScope.launch {
            settings.updateSmsRules { rules -> rules.filterNot { it.id == id } }
            _state.update { it.copy(finished = true) }
        }
    }
}
