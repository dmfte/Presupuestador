package com.tuapp.fintrack.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.settings.SettingsRepository
import com.tuapp.fintrack.domain.usecase.ExportTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val isCsvExporting: Boolean = false,
    val isJsonExporting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExportDataViewModel @Inject constructor(
    private val exportUseCase: ExportTransactionsUseCase,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val lastCsvExportTime: StateFlow<Long?> = settingsRepository.lastCsvExportTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastJsonExportTime: StateFlow<Long?> = settingsRepository.lastJsonExportTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState

    fun onExportCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCsvExporting = true, error = null) }
            try {
                val file = withContext(Dispatchers.IO) { writeCsvFile() }
                shareFile(file, "text/csv")
                settingsRepository.setLastCsvExportTime(System.currentTimeMillis())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "CSV export failed") }
            } finally {
                _uiState.update { it.copy(isCsvExporting = false) }
            }
        }
    }

    fun onExportJson() {
        viewModelScope.launch {
            _uiState.update { it.copy(isJsonExporting = true, error = null) }
            try {
                val file = withContext(Dispatchers.IO) { writeJsonFile() }
                shareFile(file, "application/json")
                settingsRepository.setLastJsonExportTime(System.currentTimeMillis())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "JSON export failed") }
            } finally {
                _uiState.update { it.copy(isJsonExporting = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun writeCsvFile(): File {
        val transactions = exportUseCase()
        val content = buildCsvContent(transactions)
        val file = File(appContext.cacheDir, "presupuestador_export_${System.currentTimeMillis()}.csv")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private suspend fun writeJsonFile(): File {
        val transactions = exportUseCase()
        val content = buildJsonContent(transactions)
        val file = File(appContext.cacheDir, "presupuestador_export_${System.currentTimeMillis()}.json")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(
            Intent.createChooser(intent, "Share export").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
