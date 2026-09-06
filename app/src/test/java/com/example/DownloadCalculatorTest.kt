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
}
