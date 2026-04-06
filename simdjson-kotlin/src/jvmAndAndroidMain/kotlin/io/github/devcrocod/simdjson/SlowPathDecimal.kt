package io.github.devcrocod.simdjson

internal class SlowPathDecimal {

    val digits = ByteArray(MAX_DIGIT_COUNT)
    var digitCount: Int = 0
    var exp10: Int = 0
    var truncated: Boolean = false
    var negative: Boolean = false

    // Before calling this method we have to make sure that the significand is within the appropriate range.
    fun computeSignificand(): Long {
        if (digitCount == 0 || exp10 < 0) {
            return 0
        }
        var significand = 0L
        for (i in 0 until exp10) {
            significand = (10 * significand) + (if (i < digitCount) digits[i].toLong() else 0)
        }
        var roundUp = false
        if (exp10 < digitCount) {
            roundUp = digits[exp10] >= 5
            if ((digits[exp10].toInt() == 5) && (exp10 + 1 == digitCount)) {
                // If the digits haven't been truncated, then we are exactly halfway between two integers. In such
                // cases, we round to even, otherwise we round up.
                roundUp = truncated || (significand and 1) == 1L
            }
        }
        return if (roundUp) ++significand else significand
    }

    fun shiftLeft(shift: Int) {
        if (digitCount == 0) {
            return
        }

        val numberOfAdditionalDigits = calculateNumberOfAdditionalDigitsAfterLeftShift(shift)
        var readIndex = digitCount - 1
        var writeIndex = digitCount - 1 + numberOfAdditionalDigits
        var n = 0L

        while (readIndex >= 0) {
            n += digits[readIndex].toLong() shl shift
            val quotient = java.lang.Long.divideUnsigned(n, 10)
            val remainder = java.lang.Long.remainderUnsigned(n, 10)
            if (writeIndex < MAX_DIGIT_COUNT) {
                digits[writeIndex] = remainder.toByte()
            } else if (remainder > 0) {
                truncated = true
            }
            n = quotient
            writeIndex--
            readIndex--
        }

        while (java.lang.Long.compareUnsigned(n, 0) > 0) {
            val quotient = java.lang.Long.divideUnsigned(n, 10)
            val remainder = java.lang.Long.remainderUnsigned(n, 10)
            if (writeIndex < MAX_DIGIT_COUNT) {
                digits[writeIndex] = remainder.toByte()
            } else if (remainder > 0) {
                truncated = true
            }
            n = quotient
            writeIndex--
        }
        digitCount += numberOfAdditionalDigits
        if (digitCount > MAX_DIGIT_COUNT) {
            digitCount = MAX_DIGIT_COUNT
        }
        exp10 += numberOfAdditionalDigits
        trimTrailingZeros()
    }

    // See https://nigeltao.github.io/blog/2020/parse-number-f64-simple.html#hpd-shifts
    private fun calculateNumberOfAdditionalDigitsAfterLeftShift(shift: Int): Int {
        val a = NumberParserTables.NUMBER_OF_ADDITIONAL_DIGITS_AFTER_LEFT_SHIFT[shift]
        val b = NumberParserTables.NUMBER_OF_ADDITIONAL_DIGITS_AFTER_LEFT_SHIFT[shift + 1]
        val newDigitCount = a shr 11
        val pow5OffsetA = 0x7FF and a
        val pow5OffsetB = 0x7FF and b

        val n = pow5OffsetB - pow5OffsetA
        for (i in 0 until n) {
            if (i >= digitCount) {
                return newDigitCount - 1
            } else if (digits[i] < NumberParserTables.POWER_OF_FIVE_DIGITS[pow5OffsetA + i]) {
                return newDigitCount - 1
            } else if (digits[i] > NumberParserTables.POWER_OF_FIVE_DIGITS[pow5OffsetA + i]) {
                return newDigitCount
            }
        }
        return newDigitCount
    }

    fun shiftRight(shift: Int) {
        var readIndex = 0
        var writeIndex = 0
        var n = 0L

        while ((n ushr shift) == 0L) {
            if (readIndex < digitCount) {
                n = (10 * n) + digits[readIndex++]
            } else if (n == 0L) {
                return
            } else {
                while ((n ushr shift) == 0L) {
                    n = 10 * n
                    readIndex++
                }
                break
            }
        }
        exp10 -= (readIndex - 1)
        val mask = (1L shl shift) - 1
        while (readIndex < digitCount) {
            val newDigit = (n ushr shift).toByte()
            n = (10 * (n and mask)) + digits[readIndex++]
            digits[writeIndex++] = newDigit
        }
        while (java.lang.Long.compareUnsigned(n, 0) > 0) {
            val newDigit = (n ushr shift).toByte()
            n = 10 * (n and mask)
            if (writeIndex < MAX_DIGIT_COUNT) {
                digits[writeIndex++] = newDigit
            } else if (newDigit > 0) {
                truncated = true
            }
        }
        digitCount = writeIndex
        trimTrailingZeros()
    }

    private fun trimTrailingZeros() {
        while ((digitCount > 0) && (digits[digitCount - 1].toInt() == 0)) {
            digitCount--
        }
    }

    fun reset() {
        digitCount = 0
        exp10 = 0
        truncated = false
    }

    companion object {
        const val MAX_DIGIT_COUNT = 800
    }
}
