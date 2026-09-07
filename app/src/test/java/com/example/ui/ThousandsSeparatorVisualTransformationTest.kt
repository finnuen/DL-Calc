package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import com.example.DownloadCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class ThousandsSeparatorVisualTransformationTest {

    @Test
    fun testEmptyAndShortStrings() {
        val emptyResult = ThousandsSeparatorVisualTransformation.filter(AnnotatedString(""))
        assertEquals("", emptyResult.text.text)

        val singleDigit = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("5"))
        assertEquals("5", singleDigit.text.text)

        val threeDigits = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("999"))
        assertEquals("999", threeDigits.text.text)
    }

    @Test
    fun testThousandsFormatting() {
        val thousand = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("1000"))
        assertEquals("1,000", thousand.text.text)

        val tenThousand = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("10000"))
        assertEquals("10,000", tenThousand.text.text)

        val hundredThousand = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("100000"))
        assertEquals("100,000", hundredThousand.text.text)

        val million = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("1000000"))
        assertEquals("1,000,000", million.text.text)
    }

    @Test
    fun testDecimalNumbers() {
        val thousandDot = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("1000."))
        assertEquals("1,000.", thousandDot.text.text)

        val thousandDecimal = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("1000.5"))
        assertEquals("1,000.5", thousandDecimal.text.text)

        val largeDecimal = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("1234567.89"))
        assertEquals("1,234,567.89", largeDecimal.text.text)
    }

    @Test
    fun testOffsetMappingForThousand() {
        // Original: "1000" (len 4) -> Transformed: "1,000" (len 5)
        val result = ThousandsSeparatorVisualTransformation.filter(AnnotatedString("1000"))
        val mapping = result.offsetMapping

        // originalToTransformed
        assertEquals(0, mapping.originalToTransformed(0)) // |1000 -> |1,000
        assertEquals(2, mapping.originalToTransformed(1)) // 1|000 -> 1,|000
        assertEquals(3, mapping.originalToTransformed(2)) // 10|00 -> 1,0|00
        assertEquals(4, mapping.originalToTransformed(3)) // 100|0 -> 1,00|0
        assertEquals(5, mapping.originalToTransformed(4)) // 1000| -> 1,000|

        // transformedToOriginal
        assertEquals(0, mapping.transformedToOriginal(0)) // |1,000 -> |1000
        assertEquals(1, mapping.transformedToOriginal(1)) // 1|,000 -> 1|000
        assertEquals(1, mapping.transformedToOriginal(2)) // 1,|000 -> 1|000
        assertEquals(2, mapping.transformedToOriginal(3)) // 1,0|00 -> 10|00
        assertEquals(3, mapping.transformedToOriginal(4)) // 1,00|0 -> 100|0
        assertEquals(4, mapping.transformedToOriginal(5)) // 1,000| -> 1000|
    }

    @Test
    fun testDownloadCalculatorFormatWithCommas() {
        assertEquals("0", DownloadCalculator.formatWithCommas(0L))
        assertEquals("999", DownloadCalculator.formatWithCommas(999L))
        assertEquals("1,000", DownloadCalculator.formatWithCommas(1000L))
        assertEquals("10,000", DownloadCalculator.formatWithCommas(10000L))
        assertEquals("1,234,567", DownloadCalculator.formatWithCommas(1234567L))
    }
}
