package com.example

import androidx.compose.runtime.Immutable
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
private val FILE_SIZE_EXPR_REGEX = Regex("""^\d*(\.\d*)?(\+\d*(\.\d*)?)*$""")

@Immutable
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
        .distinctUntilChanged { old, new ->
            old.fileSize == new.fileSize &&
                old.fileSizeUnit == new.fileSizeUnit &&
                old.speed == new.speed &&
                old.speedUnit == new.speedUnit &&
                old.timeSeconds == new.timeSeconds &&
                old.calcMode == new.calcMode
        }
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
            initialValue = DownloadTimeResult.Empty
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

    private fun parseDoubleFast(str: String): Double {
        if (str.isEmpty()) return 0.0
        val clean = if (str.indexOf(',') >= 0) str.replace(",", "") else str
        return clean.trim().toDoubleOrNull() ?: 0.0
    }

    private fun stripCommas(str: String): String =
        if (str.indexOf(',') >= 0) str.replace(",", "") else str

    private fun recalculateTotalSeconds(days: String, hours: String, minutes: String, seconds: String): String {
        val d = parseDoubleFast(days)
        val h = parseDoubleFast(hours)
        val m = parseDoubleFast(minutes)
        val s = parseDoubleFast(seconds)
        val total = d * 86400.0 + h * 3600.0 + m * 60.0 + s
        return if (total > 0.0) {
            if (total % 1.0 == 0.0) total.toLong().toString() else total.toString()
        } else {
            ""
        }
    }

    fun onFileSizeChange(newSize: String) {
        var clean = if (newSize.indexOf('=') >= 0) newSize.substringBefore('=') else newSize
        if (clean.indexOf(',') >= 0) {
            clean = clean.replace(",", "")
        }
        if (clean.indexOf(' ') >= 0) {
            clean = clean.replace(" ", "")
        }
        while (clean.indexOf("++") >= 0) {
            clean = clean.replace("++", "+")
        }
        if (clean.startsWith("+")) {
            clean = clean.removePrefix("+")
        }
        val current = _uiState.value
        if (clean == current.fileSize) return
        val isValidExpr = clean.isEmpty() || clean.matches(FILE_SIZE_EXPR_REGEX)
        if (isValidExpr) {
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && clean.isNotEmpty()) {
                if (current.speed.isNotEmpty()) {
                    newMode = CalcMode.TIME
                } else if (hasAnyTimeInput(current)) {
                    newMode = CalcMode.SPEED
                }
            }
            val updated = current.copy(
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
        if (_uiState.value.isFileSizeDropdownExpanded != expanded) {
            _uiState.value = _uiState.value.copy(isFileSizeDropdownExpanded = expanded)
        }
    }

    fun onSpeedChange(newSpeed: String) {
        val clean = stripCommas(newSpeed)
        val current = _uiState.value
        if (clean == current.speed) return
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && clean.isNotEmpty()) {
                if (current.fileSize.isNotEmpty()) {
                    newMode = CalcMode.TIME
                } else if (hasAnyTimeInput(current)) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = current.copy(
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
        if (_uiState.value.isSpeedDropdownExpanded != expanded) {
            _uiState.value = _uiState.value.copy(isSpeedDropdownExpanded = expanded)
        }
    }

    fun onDaysChange(newDays: String) {
        val clean = stripCommas(newDays)
        val current = _uiState.value
        if (clean == current.days) return
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = clean,
                hours = current.hours,
                minutes = current.minutes,
                seconds = current.seconds
            )
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (current.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (current.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = current.copy(
                days = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onHoursChange(newHours: String) {
        val clean = stripCommas(newHours)
        val current = _uiState.value
        if (clean == current.hours) return
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = current.days,
                hours = clean,
                minutes = current.minutes,
                seconds = current.seconds
            )
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (current.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (current.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = current.copy(
                hours = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onMinutesChange(newMinutes: String) {
        val clean = stripCommas(newMinutes)
        val current = _uiState.value
        if (clean == current.minutes) return
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = current.days,
                hours = current.hours,
                minutes = clean,
                seconds = current.seconds
            )
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (current.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (current.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = current.copy(
                minutes = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onSecondsChange(newSeconds: String) {
        val clean = stripCommas(newSeconds)
        val current = _uiState.value
        if (clean == current.seconds) return
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            val newTotalSeconds = recalculateTotalSeconds(
                days = current.days,
                hours = current.hours,
                minutes = current.minutes,
                seconds = clean
            )
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && (clean.isNotEmpty() || newTotalSeconds.isNotEmpty())) {
                if (current.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (current.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }
            val updated = current.copy(
                seconds = clean,
                timeSeconds = newTotalSeconds,
                calcMode = newMode
            )
            _uiState.value = updated
            persistCurrentState(updated)
        }
    }

    fun onTimeSecondsChange(newTime: String) {
        val clean = stripCommas(newTime)
        val current = _uiState.value
        if (clean == current.timeSeconds) return
        if (clean.isEmpty() || clean.matches(DECIMAL_REGEX)) {
            var newMode = current.calcMode
            if (newMode == CalcMode.NONE && clean.isNotEmpty()) {
                if (current.fileSize.isNotEmpty()) {
                    newMode = CalcMode.SPEED
                } else if (current.speed.isNotEmpty()) {
                    newMode = CalcMode.SIZE
                }
            }

            val secVal = clean.trim().toDoubleOrNull()
            var dStr = ""
            var hStr = ""
            var mStr = ""
            var sStr = ""
            if (secVal != null && secVal > 0.0) {
                val totalSec = Math.round(secVal)
                val d = totalSec / 86400
                val remD = totalSec % 86400
                val h = remD / 3600
                val remH = remD % 3600
                val m = remH / 60
                val s = remH % 60
                if (d > 0) dStr = d.toString()
                if (h > 0) hStr = h.toString()
                if (m > 0) mStr = m.toString()
                if (s > 0) sStr = s.toString()
            }

            val updated = current.copy(
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
        val current = _uiState.value
        if (current.fileSize.isEmpty()) return
        val updated = current.copy(fileSize = "")
        _uiState.value = updated
        persistCurrentState(updated, immediate = true)
    }

    fun clearSpeed() {
        val current = _uiState.value
        if (current.speed.isEmpty()) return
        val updated = current.copy(speed = "")
        _uiState.value = updated
        persistCurrentState(updated, immediate = true)
    }

    fun clearTimeSeconds() {
        val current = _uiState.value
        if (!hasAnyTimeInput(current)) return
        val updated = current.copy(
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
        val current = _uiState.value
        if (current.fileSize.isEmpty() && current.speed.isEmpty() && !hasAnyTimeInput(current) && current.calcMode == CalcMode.NONE) {
            return
        }
        val updated = current.copy(
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
