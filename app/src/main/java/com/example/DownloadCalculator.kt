package com.example

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

data class DownloadTimeResult(
    val hasResult: Boolean,
    val isBelowOneSecond: Boolean = false,
    val days: Long = 0,
    val hours: Long = 0,
    val minutes: Long = 0,
    val seconds: Long = 0,
    val totalSeconds: Long = 0,
    val formattedTotalSeconds: String = ""
) {
    /**
     * Requirement 1: "when value is zero, dont put it on copy, like '0 days'"
     * If all are 0 or below 1 second:
     * - If below 1 second: "< 1 second" (or "1< second")
     * - Only includes non-zero units. e.g. "14 minutes, 19 seconds" instead of "0 days, 0 hours, 14 minutes, 19 seconds"
     */
    fun toTimeBreakdownString(): String {
        if (!hasResult) return ""
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
        if (!hasResult) return ""
        return if (isBelowOneSecond) "1<" else totalSeconds.toString()
    }
}

object DownloadCalculator {
    fun calculate(
        fileSizeStr: String,
        fileUnit: FileSizeUnit,
        speedStr: String,
        speedUnit: SpeedUnit
    ): DownloadTimeResult {
        // Requirement 5: "for decimal, accept dot and comma"
        val cleanSizeStr = fileSizeStr.trim().replace(",", ".")
        val cleanSpeedStr = speedStr.trim().replace(",", ".")

        val size = cleanSizeStr.toDoubleOrNull() ?: return DownloadTimeResult(hasResult = false)
        val speed = cleanSpeedStr.toDoubleOrNull() ?: return DownloadTimeResult(hasResult = false)

        if (size <= 0.0 || speed <= 0.0 || size.isNaN() || speed.isNaN() || size.isInfinite() || speed.isInfinite()) {
            return DownloadTimeResult(hasResult = false)
        }

        val totalBytes = size * fileUnit.bytesMultiplier
        val bytesPerSec = speed * speedUnit.bytesPerSecMultiplier

        if (bytesPerSec <= 0.0) return DownloadTimeResult(hasResult = false)

        val totalSecondsExact = totalBytes / bytesPerSec

        // Requirement 2: "when below 1 second show '1<' in seconds"
        if (totalSecondsExact < 1.0) {
            return DownloadTimeResult(
                hasResult = true,
                isBelowOneSecond = true,
                days = 0,
                hours = 0,
                minutes = 0,
                seconds = 0,
                totalSeconds = 0,
                formattedTotalSeconds = "1<"
            )
        }

        val totalSeconds = Math.round(totalSecondsExact)

        val days = totalSeconds / 86400
        val remAfterDays = totalSeconds % 86400
        val hours = remAfterDays / 3600
        val remAfterHours = remAfterDays % 3600
        val minutes = remAfterHours / 60
        val seconds = remAfterHours % 60

        val formattedSeconds = NumberFormat.getNumberInstance(Locale.US).format(totalSeconds)

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
}
