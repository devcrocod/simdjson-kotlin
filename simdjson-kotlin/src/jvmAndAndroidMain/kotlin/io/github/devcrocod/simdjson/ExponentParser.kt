package io.github.devcrocod.simdjson

internal class ExponentParser {

    private val result = ExponentParsingResult()

    fun parse(buffer: ByteArray, currentIdx: Int, exponent: Long): ExponentParsingResult {
        var idx = currentIdx
        val negative = buffer[idx] == '-'.code.toByte()
        if (negative || buffer[idx] == '+'.code.toByte()) {
            idx++
        }
        var exponentStartIdx = idx

        var parsedExponent = 0L
        var digit = convertCharacterToDigit(buffer[idx])
        while (digit in 0..9) {
            parsedExponent = 10 * parsedExponent + digit
            idx++
            digit = convertCharacterToDigit(buffer[idx])
        }

        if (exponentStartIdx == idx) {
            throw JsonParsingException("Invalid number. Exponent indicator has to be followed by a digit.")
        }
        // Long.MAX_VALUE = 9223372036854775807 (19 digits). Therefore, any number with <= 18 digits can be safely
        // stored in a long without causing an overflow.
        val maxDigitCountLongCanAccommodate = 18
        if (idx > exponentStartIdx + maxDigitCountLongCanAccommodate) {
            // Potentially, we have an overflow here. We try to skip leading zeros.
            while (buffer[exponentStartIdx] == '0'.code.toByte()) {
                exponentStartIdx++
            }
            if (idx > exponentStartIdx + maxDigitCountLongCanAccommodate) {
                // We still have more digits than a long can safely accommodate.
                parsedExponent = 999999999999999999L
            }
        }
        val finalExponent = exponent + if (negative) -parsedExponent else parsedExponent
        return result.of(finalExponent, idx)
    }

    class ExponentParsingResult {
        var exponent: Long = 0
            private set
        var currentIdx: Int = 0
            private set

        fun of(exponent: Long, currentIdx: Int): ExponentParsingResult {
            this.exponent = exponent
            this.currentIdx = currentIdx
            return this
        }
    }

    companion object {
        fun isExponentIndicator(b: Byte): Boolean =
            b == 'e'.code.toByte() || b == 'E'.code.toByte()

        private fun convertCharacterToDigit(b: Byte): Long =
            (b.toInt() - '0'.code).toLong()
    }
}
