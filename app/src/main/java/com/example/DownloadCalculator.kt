package com.example

import androidx.compose.runtime.Immutable
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

@Immutable
data class DownloadTimeResult(
    val hasResult: Boolean,
    val isBelowOneSecond: Boolean = false,
    val days: Long = 0,
    val hours: Long = 0,
    val minutes: Long = 0,
    val seconds: Long = 0,
    val totalSeconds: Long = 0,
    val formattedDays: String = "",
    val formattedHours: String = "",
    val formattedMinutes: String = "",
    val formattedSeconds: String = "",
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

        val sb = StringBuilder()
        if (days > 0) {
            sb.append(days).append(if (days == 1L) " day" else " days")
        }
        if (hours > 0) {
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(hours).append(if (hours == 1L) " hour" else " hours")
        }
        if (minutes > 0) {
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(minutes).append(if (minutes == 1L) " minute" else " minutes")
        }
        if (seconds > 0) {
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(seconds).append(if (seconds == 1L) " second" else " seconds")
        }

        return if (sb.isEmpty()) "0 seconds" else sb.toString()
    }

    /**
     * Requirement 4: "copy button for 'in seconds' value only copy the number like 1000 instead of 1,000 seconds"
     * Requirement 2: when below 1 second show '1<' in seconds
     */
    fun toSecondsRawString(): String {
        if (!hasResult && totalSeconds <= 0 && !isBelowOneSecond) return ""
        return if (isBelowOneSecond) "1<" else totalSeconds.toString()
    }

    companion object {
        val Empty = DownloadTimeResult(hasResult = false, mode = CalcMode.TIME)
        val EmptySpeed = DownloadTimeResult(hasResult = false, mode = CalcMode.SPEED)
        val EmptySize = DownloadTimeResult(hasResult = false, mode = CalcMode.SIZE)
        val EmptyNone = DownloadTimeResult(hasResult = false, mode = CalcMode.NONE)
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
            if (kbPerSec >= 1024.0) {
                val mbPerSec = kbPerSec / 1024.0
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
            return DownloadTimeResult.Empty
        }
        if (totalSecondsExact < 1.0) {
            return DownloadTimeResult(
                hasResult = true,
                isBelowOneSecond = true,
                formattedSeconds = "1<",
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
        return DownloadTimeResult(
            hasResult = true,
            isBelowOneSecond = false,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            totalSeconds = totalSeconds,
            formattedDays = if (days > 0L) formatWithCommas(days) else "",
            formattedHours = if (hours > 0L) formatWithCommas(hours) else "",
            formattedMinutes = if (minutes > 0L) formatWithCommas(minutes) else "",
            formattedSeconds = if (seconds > 0L) formatWithCommas(seconds) else "",
            formattedTotalSeconds = formatWithCommas(totalSeconds)
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
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex < 0) return trimmed
        var commaCount = 0
        var hasDot = false
        for (i in 0 until trimmed.length) {
            when (trimmed[i]) {
                ',' -> commaCount++
                '.' -> hasDot = true
            }
        }
        val isThousandsComma = commaCount > 1 ||
            hasDot ||
            (trimmed.length - 1 - commaIndex == 3 && commaIndex > 0)
        return if (isThousandsComma) {
            trimmed.replace(",", "")
        } else {
            trimmed.replace(',', '.')
        }
    }

    fun evaluateFileSizeExpression(str: String): Double? {
        val len = str.length
        if (len == 0) return null
        if (str.indexOf('+') < 0) {
            return cleanNumericString(str).toDoubleOrNull()
        }
        var sum = 0.0
        var hasValidPart = false
        var start = 0
        while (start <= len) {
            val plusIdx = str.indexOf('+', start)
            val end = if (plusIdx < 0) len else plusIdx
            if (end > start) {
                val segment = str.substring(start, end).trim()
                if (segment.isNotEmpty()) {
                    val value = cleanNumericString(segment).toDoubleOrNull() ?: return null
                    if (value < 0.0 || value.isNaN() || value.isInfinite()) return null
                    sum += value
                    hasValidPart = true
                }
            }
            if (plusIdx < 0) break
            start = plusIdx + 1
        }
        return if (hasValidPart) sum else null
    }

    fun resolveFileSizeString(str: String): String {
        val trimmed = str.trim()
        if (!trimmed.contains('+')) return trimmed
        val parts = trimmed.split('+')
        var sum = java.math.BigDecimal.ZERO
        var validPartCount = 0
        for (part in parts) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val cleaned = cleanNumericString(p)
            val bd = cleaned.toBigDecimalOrNull() ?: return trimmed
            if (bd < java.math.BigDecimal.ZERO) return trimmed
            sum = sum.add(bd)
            validPartCount++
        }
        if (validPartCount == 0) return ""
        val stripped = sum.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            stripped.toPlainString()
        }
    }

    /**
     * Returns the "=result" suffix (e.g. "=15" for "5+5+5", or "=1,500" for "1000+500")
     * when the input contains an addition expression with at least 2 numeric terms.
     */
    fun formatFileSizeResultSuffix(str: String): String {
        val len = str.length
        if (len < 3 || str.indexOf('+') < 0) return ""
        var sum = java.math.BigDecimal.ZERO
        var validPartCount = 0
        var start = 0
        while (start <= len) {
            val plusIdx = str.indexOf('+', start)
            val end = if (plusIdx < 0) len else plusIdx
            if (end > start) {
                val p = str.substring(start, end).trim()
                if (p.isNotEmpty()) {
                    val cleaned = cleanNumericString(p)
                    val bd = cleaned.toBigDecimalOrNull() ?: return ""
                    if (bd < java.math.BigDecimal.ZERO) return ""
                    sum = sum.add(bd)
                    validPartCount++
                }
            }
            if (plusIdx < 0) break
            start = plusIdx + 1
        }
        if (validPartCount < 2) return ""
        val stripped = sum.stripTrailingZeros()
        val rawResult = if (stripped.scale() <= 0) {
            val exactLong = stripped.toBigInteger().toLong()
            formatWithCommas(exactLong)
        } else {
            val plain = stripped.toPlainString()
            val dotIdx = plain.indexOf('.')
            val intPart = if (dotIdx >= 0) plain.substring(0, dotIdx).toLongOrNull() else plain.toLongOrNull()
            if (intPart != null && dotIdx >= 0) {
                formatWithCommas(intPart) + plain.substring(dotIdx)
            } else {
                plain
            }
        }
        return "=$rawResult"
    }

    fun calculate(
        fileSizeStr: String,
        fileUnit: FileSizeUnit,
        speedStr: String,
        speedUnit: SpeedUnit,
        timeStr: String = "",
        mode: CalcMode = CalcMode.TIME
    ): DownloadTimeResult {
        val cleanSpeedStr = cleanNumericString(speedStr)
        val cleanTimeStr = cleanNumericString(timeStr)

        when (mode) {
            CalcMode.TIME -> {
                if (fileSizeStr.isBlank() || cleanSpeedStr.isEmpty()) {
                    return DownloadTimeResult.Empty
                }
                val size = evaluateFileSizeExpression(fileSizeStr) ?: return DownloadTimeResult.Empty
                val speed = cleanSpeedStr.toDoubleOrNull() ?: return DownloadTimeResult.Empty
                if (size <= 0.0 || speed <= 0.0 || size.isNaN() || speed.isNaN() || size.isInfinite() || speed.isInfinite()) {
                    return DownloadTimeResult.Empty
                }
                val totalBytes = size * fileUnit.bytesMultiplier
                val bytesPerSec = speed * speedUnit.bytesPerSecMultiplier
                if (bytesPerSec <= 0.0) return DownloadTimeResult.Empty
                val totalSecondsExact = totalBytes / bytesPerSec
                return buildTimeBreakdown(totalSecondsExact)
            }

            CalcMode.SPEED -> {
                val timeSeconds = cleanTimeStr.toDoubleOrNull()
                val timeBreakdown = if (timeSeconds != null && timeSeconds > 0.0) {
                    buildTimeBreakdown(timeSeconds)
                } else {
                    DownloadTimeResult.EmptySpeed
                }

                if (fileSizeStr.isBlank() || cleanTimeStr.isEmpty()) {
                    return if (timeBreakdown.hasResult) timeBreakdown.copy(hasResult = false, mode = mode) else DownloadTimeResult.EmptySpeed
                }
                val size = evaluateFileSizeExpression(fileSizeStr)
                    ?: return if (timeBreakdown.hasResult) timeBreakdown.copy(hasResult = false, mode = mode) else DownloadTimeResult.EmptySpeed
                if (timeSeconds == null || size <= 0.0 || timeSeconds <= 0.0 || size.isNaN() || timeSeconds.isNaN() || size.isInfinite() || timeSeconds.isInfinite()) {
                    return if (timeBreakdown.hasResult) timeBreakdown.copy(hasResult = false, mode = mode) else DownloadTimeResult.EmptySpeed
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
                    DownloadTimeResult.EmptySize
                }

                if (cleanSpeedStr.isEmpty() || cleanTimeStr.isEmpty()) {
                    return if (timeBreakdown.hasResult) timeBreakdown.copy(hasResult = false, mode = mode) else DownloadTimeResult.EmptySize
                }
                val speed = cleanSpeedStr.toDoubleOrNull()
                    ?: return if (timeBreakdown.hasResult) timeBreakdown.copy(hasResult = false, mode = mode) else DownloadTimeResult.EmptySize
                if (timeSeconds == null || speed <= 0.0 || timeSeconds <= 0.0 || speed.isNaN() || timeSeconds.isNaN() || speed.isInfinite() || timeSeconds.isInfinite()) {
                    return if (timeBreakdown.hasResult) timeBreakdown.copy(hasResult = false, mode = mode) else DownloadTimeResult.EmptySize
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
                return if (timeSeconds != null && timeSeconds > 0.0) {
                    buildTimeBreakdown(timeSeconds).copy(hasResult = false, mode = mode)
                } else {
                    DownloadTimeResult.EmptyNone
                }
            }
        }
    }
}
