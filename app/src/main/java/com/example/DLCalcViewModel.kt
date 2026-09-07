package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CalcStateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DECIMAL_REGEX = Regex("""^\d*(\.\d*)?$""")

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
        }
        .distinctUntilChanged()
        .stateIn(
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

    private var persistJob: Job? = null

    private fun persistCurrentState(state: DLCalcUiState, immediate: Boolean = false) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (!immediate) {
                delay(300L)
            }
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
        val d = days.trim().replace(",", "").toDoubleOrNull() ?: 0.0
        val h = hours.trim().replace(",", "").toDoubleOrNull() ?: 0.0
        val m = minutes.trim().replace(",", "").toDoubleOrNull() ?: 0.0
        val s = seconds.trim().replace(",", "").toDoubleOrNull() ?: 0.0
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
        val clean = newSize.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && clean.isNotEmpty()) {
                if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.TIME
                } else if (hasAnyTimeInput(_uiState.value)) {
                    newMode = CalcMode.SPEED
                }
            }
            val updated = _uiState.value.copy(
                fileSize = clean,
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
        persistCurrentState(updated, immediate = true)
    }

    fun setFileSizeDropdownExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isFileSizeDropdownExpanded = expanded)
    }

    fun onSpeedChange(newSpeed: String) {
        val clean = newSpeed.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && clean.isNotEmpty()) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.TIME
                } else if (hasAnyTimeInput(_uiState.value)) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                speed = clean,
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
        persistCurrentState(updated, immediate = true)
    }

    fun setSpeedDropdownExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isSpeedDropdownExpanded = expanded)
    }

    fun onDaysChange(newDays: String) {
        val clean = newDays.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = clean,
                hours = _uiState.value.hours,
                minutes = _uiState.value.minutes,
                seconds = _uiState.value.seconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                days = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onHoursChange(newHours: String) {
        val clean = newHours.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = _uiState.value.days,
                hours = clean,
                minutes = _uiState.value.minutes,
                seconds = _uiState.value.seconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                hours = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onMinutesChange(newMinutes: String) {
        val clean = newMinutes.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = _uiState.value.days,
                hours = _uiState.value.hours,
                minutes = clean,
                seconds = _uiState.value.seconds
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                minutes = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onSecondsChange(newSeconds: String) {
        val clean = newSeconds.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = _uiState.value.days,
                hours = _uiState.value.hours,
                minutes = _uiState.value.minutes,
                seconds = clean
            )
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = _uiState.value.copy(
                seconds = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onTimeSecondsChange(newTime: String) {
        val clean = newTime.replace(",", "")
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            var newMode = _uiState.value.calcMode
            if (newMode == CalcMode.NONE && clean.isNotEmpty()) {
                if (_uiState.value.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (_uiState.value.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }

            val secVal = clean.trim().toDoubleOrNull()
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
                timeSeconds = clean,
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
        persistCurrentState(updated, immediate = true)
    }

    fun clearSpeed() {
        val updated = _uiState.value.copy(speed = "")
        _uiState.value = updated
        persistCurrentState(updated, immediate = true)
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
        persistCurrentState(updated, immediate = true)
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
        persistCurrentState(updated, immediate = true)
    }

    fun toggleDarkMode() {
        val updated = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
        _uiState.value = updated
        persistCurrentState(updated, immediate = true)
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
