package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCalculatorTest {

    @Test
    fun testEmptyOrInvalidInputs() {
        val emptyResult = DownloadCalculator.calculate("", FileSizeUnit.GB, "", SpeedUnit.MBPS)
        assertFalse(emptyResult.hasResult)

        val invalidResult = DownloadCalculator.calculate("abc", FileSizeUnit.GB, "10", SpeedUnit.MBPS)
        assertFalse(invalidResult.hasResult)

        val zeroSpeedResult = DownloadCalculator.calculate("10", FileSizeUnit.GB, "0", SpeedUnit.MBPS)
        assertFalse(zeroSpeedResult.hasResult)
    }

    @Test
    fun testDownloadCalculation10GBat100Mbps() {
        // 10 GB = 10 * 1024 * 1024 * 1024 = 10,737,418,240 bytes
        // 100 Mbps = 100 * 125,000 = 12,500,000 bytes/sec
        // Total seconds = 10,737,418,240 / 12,500,000 = 858.99 -> 859 seconds
        // 859 seconds = 14 minutes and 19 seconds
        val result = DownloadCalculator.calculate("10", FileSizeUnit.GB, "100", SpeedUnit.MBPS)
        assertTrue(result.hasResult)
        assertEquals(0L, result.days)
        assertEquals(0L, result.hours)
        assertEquals(14L, result.minutes)
        assertEquals(19L, result.seconds)
        assertEquals(859L, result.totalSeconds)
        assertEquals("859", result.formattedTotalSeconds)
        // Check requirement 1: no zero days or zero hours on copy
        assertEquals("14 minutes, 19 seconds", result.toTimeBreakdownString())
        // Check requirement 4: only raw number copied
        assertEquals("859", result.toSecondsRawString())
    }

    @Test
    fun testCommaAndDotDecimals() {
        // 1,5 GB at 100 Mbps
        val resultComma = DownloadCalculator.calculate("1,5", FileSizeUnit.GB, "100", SpeedUnit.MBPS)
        val resultDot = DownloadCalculator.calculate("1.5", FileSizeUnit.GB, "100", SpeedUnit.MBPS)
        assertTrue(resultComma.hasResult)
        assertTrue(resultDot.hasResult)
        assertEquals(resultDot.totalSeconds, resultComma.totalSeconds)
    }

    @Test
    fun testBelowOneSecond() {
        // 1 MB at 1 Gbps -> 1,048,576 bytes / 125,000,000 bytes/sec = ~0.0084 seconds
        val result = DownloadCalculator.calculate("1", FileSizeUnit.MB, "1", SpeedUnit.GBPS)
        assertTrue(result.hasResult)
        assertTrue(result.isBelowOneSecond)
        assertEquals("1<", result.formattedTotalSeconds)
        assertEquals("1<", result.toSecondsRawString())
        assertEquals("< 1 second", result.toTimeBreakdownString())
    }

    @Test
    fun testDownloadCalculationLargeFileDays() {
        // 5 TB at 50 Mbps
        val result = DownloadCalculator.calculate("5", FileSizeUnit.TB, "50", SpeedUnit.MBPS)
        assertTrue(result.hasResult)
        assertEquals(10L, result.days)
        assertEquals(4L, result.hours)
        assertEquals(20L, result.minutes)
        assertEquals(9L, result.seconds)
        assertEquals(879609L, result.totalSeconds)
        assertEquals("879,609", result.formattedTotalSeconds)
        assertEquals("10 days, 4 hours, 20 minutes, 9 seconds", result.toTimeBreakdownString())
        assertEquals("879609", result.toSecondsRawString())
    }

    @Test
    fun testDifferentUnitsMBpsAndKBs() {
        // 100 MB at 10 MB/s = 10 seconds
        val result = DownloadCalculator.calculate("100", FileSizeUnit.MB, "10", SpeedUnit.MB_S)
        assertTrue(result.hasResult)
        assertEquals(10L, result.totalSeconds)
        assertEquals("10 seconds", result.toTimeBreakdownString())
        assertEquals("10", result.toSecondsRawString())

        // 10 MB at 1024 KB/s = 10 seconds
        val resultKb = DownloadCalculator.calculate("10", FileSizeUnit.MB, "1024", SpeedUnit.KB_S)
        assertTrue(resultKb.hasResult)
        assertEquals(10L, resultKb.totalSeconds)
    }

    @Test
    fun testTriangleCalculationSpeedFromSizeAndTime() {
        // 100 MB in 10 seconds -> Speed in MB/s should be 10
        val speedResult = DownloadCalculator.calculate(
            fileSizeStr = "100",
            fileUnit = FileSizeUnit.MB,
            speedStr = "",
            speedUnit = SpeedUnit.MB_S,
            timeStr = "10",
            mode = CalcMode.SPEED
        )
        assertTrue(speedResult.hasResult)
        assertEquals("10", speedResult.calculatedSpeed)
        assertEquals(10L, speedResult.totalSeconds)
        assertEquals("10 seconds", speedResult.toTimeBreakdownString())
    }

    @Test
    fun testTriangleCalculationSizeFromSpeedAndTime() {
        // 10 MB/s for 10 seconds -> Size in MB should be 100
        val sizeResult = DownloadCalculator.calculate(
            fileSizeStr = "",
            fileUnit = FileSizeUnit.MB,
            speedStr = "10",
            speedUnit = SpeedUnit.MB_S,
            timeStr = "10",
            mode = CalcMode.SIZE
        )
        assertTrue(sizeResult.hasResult)
        assertEquals("100", sizeResult.calculatedSize)
        assertEquals(FileSizeUnit.MB, sizeResult.calculatedSizeUnit)
        assertEquals(10L, sizeResult.totalSeconds)
        assertEquals("10 seconds", sizeResult.toTimeBreakdownString())
    }

    @Test
    fun testAutoRaiseSpeedUnitToGbpsWhen1000Mbps() {
        // 125 MB in 1 second = 125,000,000 bytes/sec = 1000 Mbps -> should auto-raise to 1 Gbps
        // 125 MB = 125 * 1024 * 1024 = 131,072,000 bytes
        // Let's create exact 1000 Mbps = 125,000,000 bytes/sec:
        // Size in MB: 125,000,000 / (1024 * 1024) = 119.20928955078125 MB
        // In 1 second, bytesPerSec = 125,000,000 -> 1000 Mbps -> 1 Gbps
        val (speedExact, bestUnit) = DownloadCalculator.determineBestSpeedUnit(125_000_000.0, SpeedUnit.MBPS)
        assertEquals(SpeedUnit.GBPS, bestUnit)
        assertEquals(1.0, speedExact, 0.001)

        // Test 2500 Mbps -> 2.5 Gbps
        val (speedExact2, bestUnit2) = DownloadCalculator.determineBestSpeedUnit(312_500_000.0, SpeedUnit.MBPS)
        assertEquals(SpeedUnit.GBPS, bestUnit2)
        assertEquals(2.5, speedExact2, 0.001)

        // Test 500 Mbps -> stays 500 Mbps
        val (speedExact3, bestUnit3) = DownloadCalculator.determineBestSpeedUnit(62_500_000.0, SpeedUnit.MBPS)
        assertEquals(SpeedUnit.MBPS, bestUnit3)
        assertEquals(500.0, speedExact3, 0.001)
    }

    @Test
    fun testAutoLowerSpeedUnitToMbpsWhenBelow1Gbps() {
        // Preferred unit is Gbps, but calculated speed is 0.5 Gbps (500 Mbps)
        val (speedExact, bestUnit) = DownloadCalculator.determineBestSpeedUnit(62_500_000.0, SpeedUnit.GBPS)
        assertEquals(SpeedUnit.MBPS, bestUnit)
        assertEquals(500.0, speedExact, 0.001)
    }

    @Test
    fun testAutoRaiseAndLowerSpeedByteRate() {
        // 2048 KB/s -> 2 MB/s
        val (speedExact1, bestUnit1) = DownloadCalculator.determineBestSpeedUnit(2048.0 * 1024.0, SpeedUnit.KB_S)
        assertEquals(SpeedUnit.MB_S, bestUnit1)
        assertEquals(2.0, speedExact1, 0.001)

        // 0.5 MB/s -> 512 KB/s
        val (speedExact2, bestUnit2) = DownloadCalculator.determineBestSpeedUnit(0.5 * 1024.0 * 1024.0, SpeedUnit.MB_S)
        assertEquals(SpeedUnit.KB_S, bestUnit2)
        assertEquals(512.0, speedExact2, 0.001)
    }

    @Test
    fun testAutoLowerSizeUnitToMBInsteadOf0Point8GB() {
        // 0.8 GB = 0.8 * 1024 * 1024 * 1024 bytes -> < 1 GB, so lowers to MB: 819.2 MB
        val (sizeExact, bestUnit) = DownloadCalculator.determineBestSizeUnit(
            0.8 * FileSizeUnit.GB.bytesMultiplier,
            FileSizeUnit.GB
        )
        assertEquals(FileSizeUnit.MB, bestUnit)
        assertEquals(819.2, sizeExact, 0.01)

        // Size calculation directly via calculate method:
        // 81.92 MB/s for 10 seconds = 819.2 MB
        val result = DownloadCalculator.calculate(
            fileSizeStr = "",
            fileUnit = FileSizeUnit.GB,
            speedStr = "81.92",
            speedUnit = SpeedUnit.MB_S,
            timeStr = "10",
            mode = CalcMode.SIZE
        )
        assertTrue(result.hasResult)
        assertEquals(FileSizeUnit.MB, result.calculatedSizeUnit)
        assertEquals("819.2", result.calculatedSize)
    }

    @Test
    fun testAutoRaiseSizeUnitToGBAndTB() {
        // 2048 MB -> 2 GB
        val (sizeExact1, bestUnit1) = DownloadCalculator.determineBestSizeUnit(
            2048.0 * FileSizeUnit.MB.bytesMultiplier,
            FileSizeUnit.MB
        )
        assertEquals(FileSizeUnit.GB, bestUnit1)
        assertEquals(2.0, sizeExact1, 0.001)

        // 2048 GB -> 2 TB
        val (sizeExact2, bestUnit2) = DownloadCalculator.determineBestSizeUnit(
            2048.0 * FileSizeUnit.GB.bytesMultiplier,
            FileSizeUnit.GB
        )
        assertEquals(FileSizeUnit.TB, bestUnit2)
        assertEquals(2.0, sizeExact2, 0.001)

        // 0.5 TB -> lowers to 512 GB
        val (sizeExact3, bestUnit3) = DownloadCalculator.determineBestSizeUnit(
            0.5 * FileSizeUnit.TB.bytesMultiplier,
            FileSizeUnit.TB
        )
        assertEquals(FileSizeUnit.GB, bestUnit3)
        assertEquals(512.0, sizeExact3, 0.001)
    }
}
