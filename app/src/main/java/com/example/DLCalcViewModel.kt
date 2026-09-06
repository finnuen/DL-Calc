package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CalcStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DECIMAL_REGEX = Regex("""^\d*([.,]\d*)?$""")

data class DLCalcUiState(
    val fileSize: String = "",
    val fileSizeUnit: FileSizeUnit = FileSizeUnit.GB,
    val isFileSizeDropdownExpanded: Boolean = false,
    val speed: String = "",
    val speedUnit: SpeedUnit = SpeedUnit.MBPS,
    val isSpeedDropdownExpanded: Boolean = false,
    val isDarkMode: Boolean = true,
    val isInitialized: Boolean = false
)

class DLCalcViewModel(private val repository: CalcStateRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DLCalcUiState())
    val uiState: StateFlow<DLCalcUiState> = _uiState.asStateFlow()

    // Cached and reactive calculation derived flow to prevent UI recomposition recalculations
    val calculationResult: StateFlow<DownloadTimeResult> = _uiState
        .combine(_uiState) { state, _ ->
            DownloadCalculator.calculate(
                state.fileSize,
                state.fileSizeUnit,
                state.speed,
                state.speedUnit
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadTimeResult(hasResult = false)
        )

    init {
        viewModelScope.launch {
            val savedState = repository.calcState.firstOrNull()
            if (savedState != null) {
                _uiState.value = _uiState.value.copy(
                    fileSize = savedState.fileSize,
                    fileSizeUnit = FileSizeUnit.fromLabel(savedState.fileSizeUnit),
                    speed = savedState.speed,
                    speedUnit = SpeedUnit.fromLabel(savedState.speedUnit),
                    isDarkMode = savedState.isDarkMode,
                    isInitialized = true
                )
            } else {
                _uiState.value = _uiState.value.copy(isInitialized = true)
            }
        }
    }

    private fun persistCurrentState(state: DLCalcUiState) {
        viewModelScope.launch {
            repository.saveState(
                fileSize = state.fileSize,
                fileSizeUnit = state.fileSizeUnit.label,
                speed = state.speed,
                speedUnit = state.speedUnit.label,
                isDarkMode = state.isDarkMode
            )
        }
    }

    fun onFileSizeChange(newSize: String) {
        // Accepts dot and comma for decimal numbers: e.g. "1.5" or "1,5"
        if (newSize.isEmpty() || newSize.matches(DECIMAL_REGEX)) {
            val updated = _uiState.value.copy(fileSize = newSize)
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onFileSizeUnitChange(newUnit: FileSizeUnit) {
        val updated = _uiState.value.copy(
            fileSizeUnit = newUnit,
            isFileSizeDropdownExpanded = false
        )
        _uiState.value = updated
        persistCurrentState(updated)
    }

    fun setFileSizeDropdownExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isFileSizeDropdownExpanded = expanded)
    }

    fun onSpeedChange(newSpeed: String) {
        // Accepts dot and comma for decimal numbers: e.g. "1.5" or "1,5"
        if (newSpeed.isEmpty() || newSpeed.matches(DECIMAL_REGEX)) {
            val updated = _uiState.value.copy(speed = newSpeed)
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onSpeedUnitChange(newUnit: SpeedUnit) {
        val updated = _uiState.value.copy(
            speedUnit = newUnit,
            isSpeedDropdownExpanded = false
        )
        _uiState.value = updated
        persistCurrentState(updated)
    }

    fun setSpeedDropdownExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isSpeedDropdownExpanded = expanded)
    }

    fun clearFileSize() {
        val updated = _uiState.value.copy(fileSize = "")
        _uiState.value = updated
        persistCurrentState(updated)
    }

    fun clearSpeed() {
        val updated = _uiState.value.copy(speed = "")
        _uiState.value = updated
        persistCurrentState(updated)
    }

    fun clearAll() {
        val updated = _uiState.value.copy(
            fileSize = "",
            speed = ""
        )
        _uiState.value = updated
        persistCurrentState(updated)
    }

    fun toggleDarkMode() {
        val updated = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
        _uiState.value = updated
        persistCurrentState(updated)
    }

    companion object {
        fun provideFactory(repository: CalcStateRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DLCalcViewModel(repository) as T
                }
            }
    }
}
