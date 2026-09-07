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
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val dotIndex = originalText.indexOf('.')
        val integerPart = if (dotIndex >= 0) originalText.substring(0, dotIndex) else originalText
        val fractionalPart = if (dotIndex >= 0) originalText.substring(dotIndex) else ""
        val intLen = integerPart.length

        // Comma formatting only applies if integer part has more than 3 characters with digits
        var hasDigits = false
        for (i in 0 until intLen) {
            if (integerPart[i].isDigit()) {
                hasDigits = true
                break
            }
        }

        if (intLen <= 3 || !hasDigits) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val formattedInteger = StringBuilder(intLen + intLen / 3)
        for (i in 0 until intLen) {
            formattedInteger.append(integerPart[i])
            val digitsRemaining = intLen - 1 - i
            if (digitsRemaining > 0 && digitsRemaining % 3 == 0 && integerPart[i].isDigit()) {
                formattedInteger.append(',')
            }
        }

        val transformedString = formattedInteger.toString() + fractionalPart
        val origLen = originalText.length
        val transLen = transformedString.length

        if (origLen == transLen) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val origToTrans = IntArray(origLen + 1)
        val transToOrig = IntArray(transLen + 1)

        var tIdx = 0
        for (oIdx in 0 until origLen) {
            origToTrans[oIdx] = tIdx
            transToOrig[tIdx] = oIdx
            tIdx++
            if (oIdx < intLen) {
                val digitsRemaining = intLen - 1 - oIdx
                if (digitsRemaining > 0 && digitsRemaining % 3 == 0 && integerPart[oIdx].isDigit()) {
                    if (tIdx <= transLen) {
                        transToOrig[tIdx] = oIdx + 1
                    }
                    tIdx++
                }
            }
        }
        origToTrans[origLen] = transLen
        while (tIdx <= transLen) {
            transToOrig[tIdx] = origLen
            tIdx++
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return origToTrans[offset.coerceIn(0, origLen)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transToOrig[offset.coerceIn(0, transLen)]
            }
        }

        return TransformedText(AnnotatedString(transformedString), offsetMapping)
    }
}
