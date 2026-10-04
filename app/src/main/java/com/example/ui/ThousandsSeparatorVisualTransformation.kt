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
    /**
     * Fast comma-formatting for display-only strings without allocating OffsetMapping arrays.
     */
    fun formatText(originalText: String): String {
        val origLen = originalText.length
        if (origLen <= 3) return originalText

        var commaCount = 0
        var scanIdx = 0
        while (scanIdx < origLen) {
            val ch = originalText[scanIdx]
            if (ch.isDigit() && (scanIdx == 0 || originalText[scanIdx - 1] != '.')) {
                var runEnd = scanIdx + 1
                while (runEnd < origLen && originalText[runEnd].isDigit()) {
                    runEnd++
                }
                val runLen = runEnd - scanIdx
                if (runLen > 3) {
                    commaCount += (runLen - 1) / 3
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

        if (commaCount == 0) return originalText

        val sb = StringBuilder(origLen + commaCount)
        var i = 0
        while (i < origLen) {
            val ch = originalText[i]
            if (ch.isDigit() && (i == 0 || originalText[i - 1] != '.')) {
                var runEnd = i + 1
                while (runEnd < origLen && originalText[runEnd].isDigit()) {
                    runEnd++
                }
                for (oIdx in i until runEnd) {
                    sb.append(originalText[oIdx])
                    if (oIdx < runEnd - 1 && (runEnd - 1 - oIdx) % 3 == 0) {
                        sb.append(',')
                    }
                }
                i = runEnd
            } else if (ch == '.') {
                sb.append(ch)
                i++
                while (i < origLen && originalText[i].isDigit()) {
                    sb.append(originalText[i])
                    i++
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val origLen = originalText.length
        // A thousands comma requires at least 4 digits in an integer run
        if (origLen <= 3) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        // Fast zero-allocation pass to count total thousands commas needed
        var commaCount = 0
        var scanIdx = 0
        while (scanIdx < origLen) {
            val ch = originalText[scanIdx]
            if (ch.isDigit() && (scanIdx == 0 || originalText[scanIdx - 1] != '.')) {
                var runEnd = scanIdx + 1
                while (runEnd < origLen && originalText[runEnd].isDigit()) {
                    runEnd++
                }
                val runLen = runEnd - scanIdx
                if (runLen > 3) {
                    commaCount += (runLen - 1) / 3
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

        if (commaCount == 0) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val transLen = origLen + commaCount
        val sb = StringBuilder(transLen)
        val origToTrans = IntArray(origLen + 1)
        val transToOrig = IntArray(transLen + 1)

        var tIdx = 0
        var i = 0
        while (i < origLen) {
            val ch = originalText[i]
            if (ch.isDigit() && (i == 0 || originalText[i - 1] != '.')) {
                var runEnd = i + 1
                while (runEnd < origLen && originalText[runEnd].isDigit()) {
                    runEnd++
                }
                for (oIdx in i until runEnd) {
                    sb.append(originalText[oIdx])
                    origToTrans[oIdx] = tIdx
                    transToOrig[tIdx] = oIdx
                    tIdx++
                    if (oIdx < runEnd - 1 && (runEnd - 1 - oIdx) % 3 == 0) {
                        sb.append(',')
                        transToOrig[tIdx] = oIdx + 1
                        tIdx++
                    }
                }
                i = runEnd
            } else if (ch == '.') {
                sb.append(ch)
                origToTrans[i] = tIdx
                transToOrig[tIdx] = i
                tIdx++
                i++
                while (i < origLen && originalText[i].isDigit()) {
                    sb.append(originalText[i])
                    origToTrans[i] = tIdx
                    transToOrig[tIdx] = i
                    tIdx++
                    i++
                }
            } else {
                sb.append(ch)
                origToTrans[i] = tIdx
                transToOrig[tIdx] = i
                tIdx++
                i++
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
