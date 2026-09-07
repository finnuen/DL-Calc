package com.example

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

enum class FileSizeUnit(val label: String, val bytesMultiplier: Double) {
    MB("MB", 1024.0 * 1024.0),
    GB("GB", 1024.0 * 1024.0 * 1024.0),
    TB("TB", 1024.0 * 1024.0 * 1024.0 * 1024.0);

    companion object {
        fun fromLabel(label: String): FileSizeUnit =
            entries.find { it.label.equals(label, ignoreCase = true) } ?: GB
    }
}

enum class SpeedUnit(val label: String, val bytesPerSecMultiplier: Double) {
    KB_S("KB/s", 1024.0),
    MB_S("MB/s", 1024.0 * 1024.0),
    MBPS("Mbps", 1_000_000.0 / 8.0),
    GBPS("Gbps", 1_000_000_000.0 / 8.0);

    companion object {
        fun fromLabel(label: String): SpeedUnit =
            entries.find { it.label.equals(label, ignoreCase = true) } ?: MBPS
    }
}

enum class CalcMode {
    NONE,
    TIME,
    SPEED,
    SIZE
}

data class DownloadTimeResult(
    val hasResult: Boolean,
    val isBelowOneSecond: Boolean = false,
    val days: Long = 0,
    val hours: Long = 0,
    val minutes: Long = 0,
    val seconds: Long = 0,
    val totalSeconds: Long = 0,
    val formattedTotalSeconds: String = "",
    val calculatedSpeed: String = "",
    val calculatedSpeedUnit: SpeedUnit? = null,
    val calculatedSize: String = "",
    val calculatedSizeUnit: FileSizeUnit? = null,
    val mode: CalcMode = CalcMode.TIME
) {
    /**
     * Requirement 1: "when value is zero, dont put it on copy, like '0 days'"
     * If all are 0 or below 1 second:
     * - If below 1 second: "< 1 second" (or "1< second")
     * - Only includes non-zero units. e.g. "14 minutes, 19 seconds" instead of "0 days, 0 hours, 14 minutes, 19 seconds"
     */
    fun toTimeBreakdownString(): String {
        if (!hasResult && totalSeconds <= 0 && !isBelowOneSecond) return ""
        if (isBelowOneSecond) return "< 1 second"

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days ${if (days == 1L) "day" else "days"}")
        if (hours > 0) parts.add("$hours ${if (hours == 1L) "hour" else "hours"}")
        if (minutes > 0) parts.add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
        if (seconds > 0) parts.add("$seconds ${if (seconds == 1L) "second" else "seconds"}")

        return if (parts.isEmpty()) {
            "0 seconds"
        } else {
            parts.joinToString(", ")
        }
    }

    /**
     * Requirement 4: "copy button for 'in seconds' value only copy the number like 1000 instead of 1,000 seconds"
     * Requirement 2: when below 1 second show '1<' in seconds
     */
    fun toSecondsRawString(): String {
        if (!hasResult && totalSeconds <= 0 && !isBelowOneSecond) return ""
        return if (isBelowOneSecond) "1<" else totalSeconds.toString()
    }
}

object DownloadCalculator {
    // ThreadLocal formatters to prevent thread contention and eliminate object allocation per calculation
    private val numberFormatter = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(Locale.US)
    }
    private val decimalFormatNormal = ThreadLocal.withInitial {
        DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    }
    private val decimalFormatSmall = ThreadLocal.withInitial {
        DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US))
    }

    private fun formatDecimal(value: Double): String {
        if (value.isNaN() || value.isInfinite() || value < 0.0) return ""
        if (value == 0.0) return "0"
        val df = if (value < 0.01) decimalFormatSmall.get() else decimalFormatNormal.get()
        return df?.format(value) ?: value.toString()
    }

    fun determineBestSpeedUnit(bytesPerSec: Double, preferredUnit: SpeedUnit): Pair<Double, SpeedUnit> {
        if (bytesPerSec <= 0.0 || bytesPerSec.isNaN() || bytesPerSec.isInfinite()) {
            return Pair(0.0, preferredUnit)
        }
        val isBitRate = (preferredUnit == SpeedUnit.MBPS || preferredUnit == SpeedUnit.GBPS)
        return if (isBitRate) {
            val mbps = (bytesPerSec * 8.0) / 1_000_000.0
            if (mbps >= 1000.0) {
                val gbps = (bytesPerSec * 8.0) / 1_000_000_000.0
                Pair(gbps, SpeedUnit.GBPS)
            } else {
                Pair(mbps, SpeedUnit.MBPS)
            }
        } else {
            val kbPerSec = bytesPerSec / 1024.0
            val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
            if (kbPerSec >= 1024.0) {
                Pair(mbPerSec, SpeedUnit.MB_S)
            } else {
                Pair(kbPerSec, SpeedUnit.KB_S)
            }
        }
    }

    fun determineBestSizeUnit(totalBytes: Double, preferredUnit: FileSizeUnit): Pair<Double, FileSizeUnit> {
        if (totalBytes <= 0.0 || totalBytes.isNaN() || totalBytes.isInfinite()) {
            return Pair(0.0, preferredUnit)
        }
        val bytesInTB = FileSizeUnit.TB.bytesMultiplier
        val bytesInGB = FileSizeUnit.GB.bytesMultiplier
        val bytesInMB = FileSizeUnit.MB.bytesMultiplier

        return when {
            totalBytes >= bytesInTB -> Pair(totalBytes / bytesInTB, FileSizeUnit.TB)
            totalBytes >= bytesInGB -> Pair(totalBytes / bytesInGB, FileSizeUnit.GB)
            else -> Pair(totalBytes / bytesInMB, FileSizeUnit.MB)
        }
    }

    private fun buildTimeBreakdown(totalSecondsExact: Double): DownloadTimeResult {
        if (totalSecondsExact.isNaN() || totalSecondsExact.isInfinite() || totalSecondsExact <= 0.0) {
            return DownloadTimeResult(hasResult = false)
        }
        if (totalSecondsExact < 1.0) {
            return DownloadTimeResult(
                hasResult = true,
                isBelowOneSecond = true,
                formattedTotalSeconds = "1<"
            )
        }
        val maxSafeSeconds = 3_153_600_000_000L
        val totalSeconds = if (totalSecondsExact >= maxSafeSeconds) {
            maxSafeSeconds
        } else {
            Math.round(totalSecondsExact)
        }
        val days = totalSeconds / 86400
        val remAfterDays = totalSeconds % 86400
        val hours = remAfterDays / 3600
        val remAfterHours = remAfterDays % 3600
        val minutes = remAfterHours / 60
        val seconds = remAfterHours % 60
        val formattedSeconds = try {
            numberFormatter.get()?.format(totalSeconds) ?: totalSeconds.toString()
        } catch (e: Exception) {
            totalSeconds.toString()
        }
        return DownloadTimeResult(
            hasResult = true,
            isBelowOneSecond = false,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            totalSeconds = totalSeconds,
            formattedTotalSeconds = formattedSeconds
        )
    }

    fun formatWithCommas(value: Long): String {
        if (value < 1000L) return value.toString()
        return try {
            numberFormatter.get()?.format(value) ?: value.toString()
        } catch (e: Exception) {
            value.toString()
        }
    }

    private fun cleanNumericString(str: String): String {
        val trimmed = str.trim()
        if (!trimmed.contains(',')) return trimmed
        val commaIndex = trimmed.indexOf(',')
        val isThousandsComma = trimmed.count { it == ',' } > 1 ||
            trimmed.contains('.') ||
            (trimmed.length - 1 - commaIndex == 3 && commaIndex > 0)
        return if (isThousandsComma) {
            trimmed.replace(",", "")
        } else {
            trimmed.replace(',', '.')
        }
    }

    fun calculate(
        fileSizeStr: String,
        fileUnit: FileSizeUnit,
        speedStr: String,
        speedUnit: SpeedUnit,
        timeStr: String = "",
        mode: CalcMode = CalcMode.TIME
    ): DownloadTimeResult {
        val cleanSizeStr = cleanNumericString(fileSizeStr)
        val cleanSpeedStr = cleanNumericString(speedStr)
        val cleanTimeStr = cleanNumericString(timeStr)

        when (mode) {
            CalcMode.TIME -> {
                if (cleanSizeStr.isEmpty() || cleanSpeedStr.isEmpty()) {
                    return DownloadTimeResult(hasResult = false, mode = mode)
                }
                val size = cleanSizeStr.toDoubleOrNull() ?: return DownloadTimeResult(hasResult = false, mode = mode)
                val speed = cleanSpeedStr.toDoubleOrNull() ?: return DownloadTimeResult(hasResult = false, mode = mode)
                if (size <= 0.0 || speed <= 0.0 || size.isNaN() || speed.isNaN() || size.isInfinite() || speed.isInfinite()) {
                    return DownloadTimeResult(hasResult = false, mode = mode)
                }
                val totalBytes = size * fileUnit.bytesMultiplier
                val bytesPerSec = speed * speedUnit.bytesPerSecMultiplier
                if (bytesPerSec <= 0.0) return DownloadTimeResult(hasResult = false, mode = mode)
                val totalSecondsExact = totalBytes / bytesPerSec
                val timeResult = buildTimeBreakdown(totalSecondsExact)
                return timeResult.copy(mode = mode)
            }

            CalcMode.SPEED -> {
                val timeSeconds = cleanTimeStr.toDoubleOrNull()
                val timeBreakdown = if (timeSeconds != null && timeSeconds > 0.0) {
                    buildTimeBreakdown(timeSeconds)
                } else {
                    DownloadTimeResult(hasResult = false)
                }

                if (cleanSizeStr.isEmpty() || cleanTimeStr.isEmpty()) {
                    return timeBreakdown.copy(hasResult = false, mode = mode)
                }
                val size = cleanSizeStr.toDoubleOrNull()
                    ?: return timeBreakdown.copy(hasResult = false, mode = mode)
                if (timeSeconds == null || size <= 0.0 || timeSeconds <= 0.0 || size.isNaN() || timeSeconds.isNaN() || size.isInfinite() || timeSeconds.isInfinite()) {
                    return timeBreakdown.copy(hasResult = false, mode = mode)
                }

                val totalBytes = size * fileUnit.bytesMultiplier
                val bytesPerSec = totalBytes / timeSeconds
                val (speedExact, bestSpeedUnit) = determineBestSpeedUnit(bytesPerSec, speedUnit)
                val calculatedSpeedStr = formatDecimal(speedExact)

                return timeBreakdown.copy(
                    hasResult = calculatedSpeedStr.isNotEmpty(),
                    calculatedSpeed = calculatedSpeedStr,
                    calculatedSpeedUnit = bestSpeedUnit,
                    mode = mode
                )
            }

            CalcMode.SIZE -> {
                val timeSeconds = cleanTimeStr.toDoubleOrNull()
                val timeBreakdown = if (timeSeconds != null && timeSeconds > 0.0) {
                    buildTimeBreakdown(timeSeconds)
                } else {
                    DownloadTimeResult(hasResult = false)
                }

                if (cleanSpeedStr.isEmpty() || cleanTimeStr.isEmpty()) {
                    return timeBreakdown.copy(hasResult = false, mode = mode)
                }
                val speed = cleanSpeedStr.toDoubleOrNull()
                    ?: return timeBreakdown.copy(hasResult = false, mode = mode)
                if (timeSeconds == null || speed <= 0.0 || timeSeconds <= 0.0 || speed.isNaN() || timeSeconds.isNaN() || speed.isInfinite() || timeSeconds.isInfinite()) {
                    return timeBreakdown.copy(hasResult = false, mode = mode)
                }

                val bytesPerSec = speed * speedUnit.bytesPerSecMultiplier
                val totalBytes = bytesPerSec * timeSeconds
                val (sizeExact, bestSizeUnit) = determineBestSizeUnit(totalBytes, fileUnit)
                val calculatedSizeStr = formatDecimal(sizeExact)

                return timeBreakdown.copy(
                    hasResult = calculatedSizeStr.isNotEmpty(),
                    calculatedSize = calculatedSizeStr,
                    calculatedSizeUnit = bestSizeUnit,
                    mode = mode
                )
            }

            CalcMode.NONE -> {
                val timeSeconds = cleanTimeStr.toDoubleOrNull()
                val timeBreakdown = if (timeSeconds != null && timeSeconds > 0.0) {
                    buildTimeBreakdown(timeSeconds)
                } else {
                    DownloadTimeResult(hasResult = false)
                }
                return timeBreakdown.copy(hasResult = false, mode = mode)
            }
        }
    }
}
