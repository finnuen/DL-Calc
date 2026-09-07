package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CalcStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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
    val days: String = "",
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val timeSeconds: String = "",
    val calcMode: CalcMode = CalcMode.NONE,
    val isDarkMode: Boolean = true,
    val isInitialized: Boolean = false
)

class DLCalcViewModel(private val repository: CalcStateRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DLCalcUiState())
    val uiState: StateFlow<DLCalcUiState> = _uiState.asStateFlow()

    // Cached and reactive calculation derived flow to prevent UI recomposition recalculations
    val calculationResult: StateFlow<DownloadTimeResult> = _uiState
        .map { state ->
            DownloadCalculator.calculate(
                fileSizeStr = state.fileSize,
                fileUnit = state.fileSizeUnit,
                speedStr = state.speed,
                speedUnit = state.speedUnit,
                timeStr = state.timeSeconds,
                mode = state.calcMode
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadTimeResult(hasResult = false)
        )

    init {
        viewModelScope.launch {
            try {
                val savedState = repository.calcState.firstOrNull()
                if (savedState != null) {
                    val restoredMode = try {
                        CalcMode.valueOf(savedState.calcMode)
                    } catch (e: Exception) {
                        if (savedState.fileSize.isNotEmpty() && savedState.speed.isNotEmpty()) {
                            CalcMode.TIME
                        } else {
                            CalcMode.NONE
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        fileSize = savedState.fileSize,
                        fileSizeUnit = FileSizeUnit.fromLabel(savedState.fileSizeUnit),
                        speed = savedState.speed,
                        speedUnit = SpeedUnit.fromLabel(savedState.speedUnit),
                        days = savedState.days,
                        hours = savedState.hours,
                        minutes = savedState.minutes,
                        seconds = savedState.seconds,
                        timeSeconds = savedState.timeSeconds,
                        calcMode = restoredMode,
                        isDarkMode = savedState.isDarkMode,
                        isInitialized = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isInitialized = true)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isInitialized = true)
            }
        }
    }

    private fun persistCurrentState(state: DLCalcUiState) {
        viewModelScope.launch {
            try {
                repository.saveState(
                    fileSize = state.fileSize,
                    fileSizeUnit = state.fileSizeUnit.label,
                    speed = state.speed,
                    speedUnit = state.speedUnit.label,
                    days = state.days,
                    hours = state.hours,
                    minutes = state.minutes,
                    seconds = state.seconds,
                    timeSeconds = state.timeSeconds,
                    calcMode = state.calcMode.name,
                    isDarkMode = state.isDarkMode
                )
            } catch (e: Exception) {
                // Ignore failure to persist to prevent crashing
            }
        }
    }

    private fun hasAnyTimeInput(state: DLCalcUiState): Boolean {
        return state.timeSeconds.isNotEmpty() ||
               state.days.isNotEmpty() ||
               state.hours.isNotEmpty() ||
               state.minutes.isNotEmpty() ||
               state.seconds.isNotEmpty()
    }

    private fun recalculateTotalSeconds(days: String, hours: String, minutes: String, seconds: String): String {
        val d = days.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val h = hours.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val m = minutes.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val s = seconds.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val total = d * 86400.0 + h * 3600.0 + m * 60.0 + s
        return if (total > 0.0) {
            if (total % 1.0 == 0.0) total.toLong().toString() else total.toString()
        } else if (days.isNotEmpty() || hours.isNotEmpty() || minutes.isNotEmpty() || seconds.isNotEmpty()) {
            "0"
        } else {
            ""
        }
    }

    fun onFileSizeChange(newSize: String) {
        // Accepts dot and comma for decimal numbers: e.g. "1.5" or "1,5"
        if (newSize.isEmpty() || newSize.matches(DECIMAL_REGEX)) {
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && newSize.isNotEmpty()) {
                if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.TIME
                } else if (hasAnyTimeInput(_uiState.value)) {
                    newMode = CalcMode.SPEED
                }
            }
            val updated = _uiState.value.copy(
                fileSize = newSize,
                calcMode = newMode
            )
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
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && newSpeed.isNotEmpty()) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.TIME
                } else if (hasAnyTimeInput(_uiState.value)) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                speed = newSpeed,
                calcMode = newMode
            )
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

    fun onDaysChange(newDays: String) {
        if (newDays.isEmpty() || newDays.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = newDays,
                hours = _uiState.value.hours,
                minutes = _uiState.value.minutes,
                seconds = _uiState.value.seconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (newDays.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                days = newDays,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onHoursChange(newHours: String) {
        if (newHours.isEmpty() || newHours.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = _uiState.value.days,
                hours = newHours,
                minutes = _uiState.value.minutes,
                seconds = _uiState.value.seconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (newHours.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                hours = newHours,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onMinutesChange(newMinutes: String) {
        if (newMinutes.isEmpty() || newMinutes.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = _uiState.value.days,
                hours = _uiState.value.hours,
                minutes = newMinutes,
                seconds = _uiState.value.seconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (newMinutes.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                minutes = newMinutes,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onSecondsChange(newSeconds: String) {
        if (newSeconds.isEmpty() || newSeconds.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = _uiState.value.days,
                hours = _uiState.value.hours,
                minutes = _uiState.value.minutes,
                seconds = newSeconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (newSeconds.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                seconds = newSeconds,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onTimeSecondsChange(newTime: String) {
        // Accepts dot and comma for decimal numbers: e.g. "1.5" or "1,5"
        if (newTime.isEmpty() || newTime.matches(DECIMAL_REGEX)) {
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && newTime.isNotEmpty()) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }

            val secVal = newTime.trim().replace(',', '.').toDoubleOrNull()
            val (dStr, hStr, mStr, sStr) = if (secVal != null && secVal > 0.0) {
                val totalSec = Math.round(secVal)
                val d = totalSec / 86400
                val remD = totalSec % 86400
                val h = remD / 3600
                val remH = remD % 3600
                val m = remH / 60
                val s = remH % 60
                listOf(
                    if (d > 0) d.toString() else "",
                    if (h > 0 || d > 0) h.toString() else "",
                    if (m > 0 || h > 0 || d > 0) m.toString() else "",
                    s.toString()
                )
            } else {
                listOf("", "", "", "")
            }

            val updated = _uiState.value.copy(
                timeSeconds = newTime,
                days = dStr,
                hours = hStr,
                minutes = mStr,
                seconds = sStr,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
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

    fun clearTimeSeconds() {
        val updated = _uiState.value.copy(
            timeSeconds = "",
            days = "",
            hours = "",
            minutes = "",
            seconds = ""
        )
        _uiState.value = updated
        persistCurrentState(updated)
    }

    fun clearAll() {
        val updated = _uiState.value.copy(
            fileSize = "",
            speed = "",
            days = "",
            hours = "",
            minutes = "",
            seconds = "",
            timeSeconds = "",
            calcMode = CalcMode.NONE
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
