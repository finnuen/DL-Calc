package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Automatically formats numeric input strings with commas for thousands (e.g. 1000 -> 1,000)
 * while preserving precise bidirectional cursor offset mapping for seamless typing and editing.
 */
object ThousandsSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val origLen = originalText.length
        // A thousands comma requires at least 4 digits in an integer run
        if (origLen <= 3) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        // Fast zero-allocation pass to check if any integer digit run exceeds 3 digits
        var hasRunOverThree = false
        var scanIdx = 0
        while (scanIdx < origLen) {
            val ch = originalText[scanIdx]
            if (ch.isDigit() && (scanIdx == 0 || originalText[scanIdx - 1] != '.')) {
                var runEnd = scanIdx + 1
                while (runEnd < origLen && originalText[runEnd].isDigit()) {
                    runEnd++
                }
                if (runEnd - scanIdx > 3) {
                    hasRunOverThree = true
                    break
                }
                scanIdx = runEnd
            } else if (ch == '.') {
                scanIdx++
                while (scanIdx < origLen && originalText[scanIdx].isDigit()) {
                    scanIdx++
                }
            } else {
                scanIdx++
            }
        }

        if (!hasRunOverThree) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val insertCommaAfter = BooleanArray(origLen)
        var commaCount = 0

        var i = 0
        while (i < origLen) {
            if (originalText[i].isDigit() && (i == 0 || originalText[i - 1] != '.')) {
                var runEnd = i + 1
                while (runEnd < origLen && originalText[runEnd].isDigit()) {
                    runEnd++
                }
                val runLen = runEnd - i
                if (runLen > 3) {
                    for (j in i until runEnd - 1) {
                        val digitsRemaining = runEnd - 1 - j
                        if (digitsRemaining > 0 && digitsRemaining % 3 == 0) {
                            insertCommaAfter[j] = true
                            commaCount++
                        }
                    }
                }
                i = runEnd
            } else if (originalText[i] == '.') {
                i++
                while (i < origLen && originalText[i].isDigit()) {
                    i++
                }
            } else {
                i++
            }
        }

        val transLen = origLen + commaCount
        val sb = StringBuilder(transLen)
        val origToTrans = IntArray(origLen + 1)
        val transToOrig = IntArray(transLen + 1)

        var tIdx = 0
        for (oIdx in 0 until origLen) {
            sb.append(originalText[oIdx])
            origToTrans[oIdx] = tIdx
            transToOrig[tIdx] = oIdx
            tIdx++
            if (insertCommaAfter[oIdx]) {
                sb.append(',')
                transToOrig[tIdx] = oIdx + 1
                tIdx++
            }
        }
        origToTrans[origLen] = transLen
        transToOrig[transLen] = origLen

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return origToTrans[offset.coerceIn(0, origLen)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transToOrig[offset.coerceIn(0, transLen)]
            }
        }

        return TransformedText(AnnotatedString(sb.toString()), offsetMapping)
    }
}

/**
 * Formats file size expressions with thousands commas and optionally appends "=result"
 * (e.g., "5+5+5=15") when the file size text field is not focused.
 */
class FileSizeVisualTransformation(private val showResultSuffix: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val baseTransformed = ThousandsSeparatorVisualTransformation.filter(text)
        if (!showResultSuffix) {
            return baseTransformed
        }
        val originalText = text.text
        val suffix = com.example.DownloadCalculator.formatFileSizeResultSuffix(originalText)
        if (suffix.isEmpty()) {
            return baseTransformed
        }
        val baseText = baseTransformed.text.text
        val baseMapping = baseTransformed.offsetMapping
        val origLen = originalText.length
        val baseLen = baseText.length
        val combinedText = baseText + suffix
        val totalLen = combinedText.length

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return baseMapping.originalToTransformed(offset.coerceIn(0, origLen))
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, totalLen)
                return if (clamped <= baseLen) {
                    baseMapping.transformedToOriginal(clamped)
                } else {
                    origLen
                }
            }
        }

        return TransformedText(AnnotatedString(combinedText), offsetMapping)
    }

    companion object {
        val Focused = FileSizeVisualTransformation(showResultSuffix = false)
        val Unfocused = FileSizeVisualTransformation(showResultSuffix = true)
    }
}
