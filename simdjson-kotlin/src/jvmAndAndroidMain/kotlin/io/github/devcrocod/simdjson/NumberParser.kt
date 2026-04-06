package io.github.devcrocod.simdjson

internal class NumberParser {

    private val digitsParsingResult = DigitsParsingResult()
    private val exponentParser = ExponentParser()
    private val doubleParser = DoubleParser()
    private val floatParser = FloatParser()

    fun parseNumber(buffer: ByteArray, offset: Int, tape: Tape) {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        var result = parseDigits(buffer, currentIdx, 0)
        var digits = result.digits
        currentIdx = result.currentIdx
        var digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        var exponent = 0L
        var floatingPointNumber = false
        if ('.'.code.toByte() == buffer[currentIdx]) {
            floatingPointNumber = true
            currentIdx++
            val firstIdxAfterPeriod = currentIdx
            result = parseDigits(buffer, currentIdx, digits)
            digits = result.digits
            currentIdx = result.currentIdx
            exponent = (firstIdxAfterPeriod - currentIdx).toLong()
            if (exponent == 0L) {
                throw JsonParsingException("Invalid number. Decimal point has to be followed by a digit.")
            }
            digitCount = currentIdx - digitsStartIdx
        }
        if (ExponentParser.isExponentIndicator(buffer[currentIdx])) {
            floatingPointNumber = true
            currentIdx++
            val exponentParsingResult = exponentParser.parse(buffer, currentIdx, exponent)
            exponent = exponentParsingResult.exponent
            currentIdx = exponentParsingResult.currentIdx
        }
        if (!CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }
        if (floatingPointNumber) {
            val value = doubleParser.parse(buffer, offset, negative, digitsStartIdx, digitCount, digits, exponent)
            tape.appendDouble(value)
        } else {
            if (isOutOfLongRange(negative, digits, digitCount)) {
                // Try to store as uint64 for non-negative values that exceed Long range
                if (negative) {
                    throw JsonParsingException("Number value is out of long range ([${Long.MIN_VALUE}, ${Long.MAX_VALUE}]).")
                }
                if (isOutOfULongRange(digits, digitCount, buffer[digitsStartIdx])) {
                    throw JsonParsingException("Number value is out of unsigned long range ([0, ${ULong.MAX_VALUE}]).")
                }
                tape.appendUInt64(digits)
            } else {
                tape.appendInt64(if (negative) digits.inv() + 1 else digits)
            }
        }
    }

    fun parseLong(buffer: ByteArray, len: Int, offset: Int): Long {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        val result = parseDigits(buffer, currentIdx, 0)
        val digits = result.digits
        currentIdx = result.currentIdx
        val digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }
        if (isOutOfLongRange(negative, digits, digitCount)) {
            throw JsonParsingException("Number value is out of long range ([${Long.MIN_VALUE}, ${Long.MAX_VALUE}]).")
        }
        return if (negative) digits.inv() + 1 else digits
    }

    fun parseByte(buffer: ByteArray, len: Int, offset: Int): Byte {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        val result = parseDigits(buffer, currentIdx, 0)
        val digits = result.digits
        currentIdx = result.currentIdx
        val digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }
        if (isOutOfByteRange(negative, digits, digitCount)) {
            throw JsonParsingException("Number value is out of byte range ([${Byte.MIN_VALUE}, ${Byte.MAX_VALUE}]).")
        }
        return (if (negative) digits.inv() + 1 else digits).toByte()
    }

    fun parseShort(buffer: ByteArray, len: Int, offset: Int): Short {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        val result = parseDigits(buffer, currentIdx, 0)
        val digits = result.digits
        currentIdx = result.currentIdx
        val digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }
        if (isOutOfShortRange(negative, digits, digitCount)) {
            throw JsonParsingException("Number value is out of short range ([${Short.MIN_VALUE}, ${Short.MAX_VALUE}]).")
        }
        return (if (negative) digits.inv() + 1 else digits).toShort()
    }

    fun parseInt(buffer: ByteArray, len: Int, offset: Int): Int {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        val result = parseDigits(buffer, currentIdx, 0)
        val digits = result.digits
        currentIdx = result.currentIdx
        val digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }
        if (isOutOfIntRange(negative, digits, digitCount)) {
            throw JsonParsingException("Number value is out of int range ([${Int.MIN_VALUE}, ${Int.MAX_VALUE}]).")
        }
        return (if (negative) digits.inv() + 1 else digits).toInt()
    }

    fun parseULong(buffer: ByteArray, len: Int, offset: Int): ULong {
        if (buffer[offset] == '-'.code.toByte()) {
            throw JsonParsingException("Invalid unsigned number. Minus sign is not allowed.")
        }

        val digitsStartIdx = offset
        val result = parseDigits(buffer, offset, 0)
        val digits = result.digits
        val currentIdx = result.currentIdx
        val digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Expected a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }
        if (isOutOfULongRange(digits, digitCount, buffer[digitsStartIdx])) {
            throw JsonParsingException("Number value is out of unsigned long range ([0, ${ULong.MAX_VALUE}]).")
        }
        return digits.toULong()
    }

    fun parseFloat(buffer: ByteArray, len: Int, offset: Int): Float {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        var result = parseDigits(buffer, currentIdx, 0)
        currentIdx = result.currentIdx
        var digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        var exponent = 0L
        var floatingPointNumber = false
        if ('.'.code.toByte() == buffer[currentIdx]) {
            floatingPointNumber = true
            currentIdx++
            val firstIdxAfterPeriod = currentIdx
            result = parseDigits(buffer, currentIdx, result.digits)
            currentIdx = result.currentIdx
            exponent = (firstIdxAfterPeriod - currentIdx).toLong()
            if (exponent == 0L) {
                throw JsonParsingException("Invalid number. Decimal point has to be followed by a digit.")
            }
            digitCount = currentIdx - digitsStartIdx
        }
        if (ExponentParser.isExponentIndicator(buffer[currentIdx])) {
            floatingPointNumber = true
            currentIdx++
            val exponentParsingResult = exponentParser.parse(buffer, currentIdx, exponent)
            exponent = exponentParsingResult.exponent
            currentIdx = exponentParsingResult.currentIdx
        }
        if (!floatingPointNumber) {
            throw JsonParsingException("Invalid floating-point number. Fraction or exponent part is missing.")
        }
        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }

        return floatParser.parse(buffer, offset, negative, digitsStartIdx, digitCount, result.digits, exponent)
    }

    fun parseDouble(buffer: ByteArray, len: Int, offset: Int): Double {
        val negative = buffer[offset] == '-'.code.toByte()

        var currentIdx = if (negative) offset + 1 else offset

        val digitsStartIdx = currentIdx
        var result = parseDigits(buffer, currentIdx, 0)
        currentIdx = result.currentIdx
        var digitCount = currentIdx - digitsStartIdx
        if (digitCount == 0) {
            throw JsonParsingException("Invalid number. Minus has to be followed by a digit.")
        }
        if ('0'.code.toByte() == buffer[digitsStartIdx] && digitCount > 1) {
            throw JsonParsingException("Invalid number. Leading zeroes are not allowed.")
        }

        var exponent = 0L
        var floatingPointNumber = false
        if ('.'.code.toByte() == buffer[currentIdx]) {
            floatingPointNumber = true
            currentIdx++
            val firstIdxAfterPeriod = currentIdx
            result = parseDigits(buffer, currentIdx, result.digits)
            currentIdx = result.currentIdx
            exponent = (firstIdxAfterPeriod - currentIdx).toLong()
            if (exponent == 0L) {
                throw JsonParsingException("Invalid number. Decimal point has to be followed by a digit.")
            }
            digitCount = currentIdx - digitsStartIdx
        }
        if (ExponentParser.isExponentIndicator(buffer[currentIdx])) {
            floatingPointNumber = true
            currentIdx++
            val exponentParsingResult = exponentParser.parse(buffer, currentIdx, exponent)
            exponent = exponentParsingResult.exponent
            currentIdx = exponentParsingResult.currentIdx
        }
        if (!floatingPointNumber) {
            throw JsonParsingException("Invalid floating-point number. Fraction or exponent part is missing.")
        }
        if (currentIdx < len && !CharacterUtils.isStructuralOrWhitespace(buffer[currentIdx])) {
            throw JsonParsingException("Number has to be followed by a structural character or whitespace.")
        }

        return doubleParser.parse(buffer, offset, negative, digitsStartIdx, digitCount, result.digits, exponent)
    }

    private fun parseDigits(buffer: ByteArray, currentIdx: Int, digits: Long): DigitsParsingResult {
        var idx = currentIdx
        var d = digits
        var digit = convertCharacterToDigit(buffer[idx])
        while (digit in 0..9) {
            d = 10 * d + digit.toLong()
            idx++
            digit = convertCharacterToDigit(buffer[idx])
        }
        return digitsParsingResult.of(d, idx)
    }

    private class DigitsParsingResult {
        var digits: Long = 0
            private set
        var currentIdx: Int = 0
            private set

        fun of(digits: Long, currentIdx: Int): DigitsParsingResult {
            this.digits = digits
            this.currentIdx = currentIdx
            return this
        }
    }

    companion object {
        private const val BYTE_MAX_DIGIT_COUNT = 3
        private const val BYTE_MAX_ABS_VALUE = 128L
        private const val SHORT_MAX_DIGIT_COUNT = 5
        private const val SHORT_MAX_ABS_VALUE = 32768L
        private const val INT_MAX_DIGIT_COUNT = 10
        private const val INT_MAX_ABS_VALUE = 2147483648L
        private const val LONG_MAX_DIGIT_COUNT = 19
        private const val ULONG_MAX_DIGIT_COUNT = 20

        private fun convertCharacterToDigit(b: Byte): Byte {
            return (b.toInt() - '0'.code).toByte()
        }

        private fun isOutOfByteRange(negative: Boolean, digits: Long, digitCount: Int): Boolean {
            if (digitCount < BYTE_MAX_DIGIT_COUNT) return false
            if (digitCount > BYTE_MAX_DIGIT_COUNT) return true
            return if (negative) digits > BYTE_MAX_ABS_VALUE else digits > Byte.MAX_VALUE
        }

        private fun isOutOfShortRange(negative: Boolean, digits: Long, digitCount: Int): Boolean {
            if (digitCount < SHORT_MAX_DIGIT_COUNT) return false
            if (digitCount > SHORT_MAX_DIGIT_COUNT) return true
            return if (negative) digits > SHORT_MAX_ABS_VALUE else digits > Short.MAX_VALUE
        }

        private fun isOutOfIntRange(negative: Boolean, digits: Long, digitCount: Int): Boolean {
            if (digitCount < INT_MAX_DIGIT_COUNT) {
                return false
            }
            if (digitCount > INT_MAX_DIGIT_COUNT) {
                return true
            }
            if (negative) {
                return digits > INT_MAX_ABS_VALUE
            }
            return digits > Int.MAX_VALUE
        }

        private fun isOutOfLongRange(negative: Boolean, digits: Long, digitCount: Int): Boolean {
            if (digitCount < LONG_MAX_DIGIT_COUNT) {
                return false
            }
            if (digitCount > LONG_MAX_DIGIT_COUNT) {
                return true
            }
            if (negative && digits == Long.MIN_VALUE) {
                // The maximum value we can store in a long is 9223372036854775807. When we try to store 9223372036854775808,
                // a long wraps around, resulting in -9223372036854775808 (Long.MIN_VALUE). If the number we are parsing is
                // negative, and we've attempted to store 9223372036854775808 in "digits", we can be sure that we are
                // dealing with Long.MIN_VALUE, which obviously does not fall outside the acceptable range.
                return false
            }
            return digits < 0
        }

        private fun isOutOfULongRange(digits: Long, digitCount: Int, firstDigit: Byte): Boolean {
            if (digitCount < ULONG_MAX_DIGIT_COUNT) {
                return false
            }
            if (digitCount > ULONG_MAX_DIGIT_COUNT) {
                return true
            }
            // Exactly 20 digits: max ULong is 18446744073709551615, starting with '1'.
            // If the first digit is '2'-'9', it's always overflow.
            if (firstDigit != '1'.code.toByte()) {
                return true
            }
            // If the first digit is '1' and overflow occurred during the multiplication loop,
            // the signed value wraps to a non-negative number (<= Long.MAX_VALUE).
            return digits in 0..Long.MAX_VALUE
        }
    }
}
