package io.github.devcrocod.simdjson

import kotlin.math.abs

internal class DoubleParser {

    private val slowPathDecimal = SlowPathDecimal()
    private val exponentParser = ExponentParser()

    fun parse(
        buffer: ByteArray, offset: Int, negative: Boolean,
        digitsStartIdx: Int, digitCount: Int, digits: Long, exponent: Long
    ): Double {
        return if (shouldBeHandledBySlowPath(buffer, digitsStartIdx, digitCount)) {
            slowlyParseDouble(buffer, offset)
        } else {
            computeDouble(negative, digits, exponent)
        }
    }

    // The following parser is based on the idea described in
    // https://nigeltao.github.io/blog/2020/parse-number-f64-simple.html and implemented in
    // https://github.com/simdjson/simdjson/blob/caff09cafceb0f5f6fc9109236d6dd09ac4bc0d8/src/from_chars.cpp
    private fun slowlyParseDouble(buffer: ByteArray, offset: Int): Double {
        val decimal = slowPathDecimal
        decimal.reset()

        decimal.negative = buffer[offset] == '-'.code.toByte()
        var currentIdx = if (decimal.negative) offset + 1 else offset
        var exp10 = 0L

        currentIdx = skipZeros(buffer, currentIdx)
        currentIdx = parseDigits(buffer, decimal, currentIdx)
        if (buffer[currentIdx] == '.'.code.toByte()) {
            currentIdx++
            val firstIdxAfterPeriod = currentIdx
            if (decimal.digitCount == 0) {
                currentIdx = skipZeros(buffer, currentIdx)
            }
            currentIdx = parseDigits(buffer, decimal, currentIdx)
            exp10 = (firstIdxAfterPeriod - currentIdx).toLong()
        }

        var currentIdxMovingBackwards = currentIdx - 1
        var trailingZeros = 0
        // Here, we also skip the period to handle cases like 100000000000000000000.000000
        while (buffer[currentIdxMovingBackwards] == '0'.code.toByte() || buffer[currentIdxMovingBackwards] == '.'.code.toByte()) {
            if (buffer[currentIdxMovingBackwards] == '0'.code.toByte()) {
                trailingZeros++
            }
            currentIdxMovingBackwards--
        }
        exp10 += decimal.digitCount
        decimal.digitCount -= trailingZeros

        if (decimal.digitCount > SlowPathDecimal.MAX_DIGIT_COUNT) {
            decimal.digitCount = SlowPathDecimal.MAX_DIGIT_COUNT
            decimal.truncated = true
        }

        if (ExponentParser.isExponentIndicator(buffer[currentIdx])) {
            currentIdx++
            exp10 = exponentParser.parse(buffer, currentIdx, exp10).exponent
        }

        // At this point, the number we are parsing is represented in the following way: w * 10^exp10, where -1 < w < 1.
        if (exp10 <= -324) {
            // We know that -1e-324 < w * 10^exp10 < 1e-324. In binary64 -1e-324 = -0.0 and 1e-324 = +0.0, so we can
            // safely return +/-0.0.
            return zero(decimal.negative)
        } else if (exp10 >= 310) {
            // We know that either w * 10^exp10 <= -0.1e310 or w * 10^exp10 >= 0.1e310.
            // In binary64 -0.1e310 = -inf and 0.1e310 = +inf, so we can safely return +/-inf.
            return infinity(decimal.negative)
        }

        decimal.exp10 = exp10.toInt()
        var exp2 = 0

        // We start the following loop with the decimal in the form of w * 10^exp10. After a series of
        // right-shifts (dividing by a power of 2), we transform the decimal into w' * 2^exp2 * 10^exp10',
        // where exp10' is <= 0. Resultantly, w' * 10^exp10' is in the range of [0, 1).
        while (decimal.exp10 > 0) {
            val shift = resolveShiftDistanceBasedOnExponent10(decimal.exp10)
            decimal.shiftRight(shift)
            exp2 += shift
        }

        // Now, we are left-shifting to get to the point where w'' * 10^exp10'' is within the range of [1/2, 1).
        while (decimal.exp10 <= 0) {
            val shift: Int
            if (decimal.exp10 == 0) {
                if (decimal.digits[0] >= 5) {
                    break
                }
                shift = if (decimal.digits[0] < 2) 2 else 1
            } else {
                shift = resolveShiftDistanceBasedOnExponent10(-decimal.exp10)
            }
            decimal.shiftLeft(shift)
            exp2 -= shift
        }

        // Here, w'' * 10^exp10'' falls within the range of [1/2, 1). In binary64, the significand must be within the
        // range of [1, 2). We can get to the target range by decreasing the binary exponent. Resultantly, the decimal
        // is represented as w'' * 10^exp10'' * 2^exp2, where w'' * 10^exp10'' is in the range of [1, 2).
        exp2--

        while (IEEE64_MIN_FINITE_NUMBER_EXPONENT > exp2) {
            var n = IEEE64_MIN_FINITE_NUMBER_EXPONENT - exp2
            if (n > SLOW_PATH_MAX_SHIFT) {
                n = SLOW_PATH_MAX_SHIFT
            }
            decimal.shiftRight(n)
            exp2 += n
        }

        // To conform to the IEEE 754 standard, the binary significand must fall within the range of [2^52, 2^53). Hence,
        // we perform the following multiplication. If, after this step, the significand is less than 2^52, we have a
        // subnormal number, which we will address later.
        decimal.shiftLeft(IEEE64_SIGNIFICAND_SIZE_IN_BITS)

        var significand2 = decimal.computeSignificand()
        if (significand2 >= (1L shl IEEE64_SIGNIFICAND_SIZE_IN_BITS)) {
            // If we've reached here, it means that rounding has caused an overflow. We need to divide the significand
            // by 2 and update the exponent accordingly.
            significand2 = significand2 shr 1
            exp2++
        }

        if (significand2 < (1L shl IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT)) {
            exp2 = IEEE64_SUBNORMAL_EXPONENT
        }
        if (exp2 > IEEE64_MAX_FINITE_NUMBER_EXPONENT) {
            return infinity(decimal.negative)
        }
        return toDouble(decimal.negative, significand2, exp2.toLong())
    }

    private fun skipZeros(buffer: ByteArray, currentIdx: Int): Int {
        var idx = currentIdx
        while (buffer[idx] == '0'.code.toByte()) {
            idx++
        }
        return idx
    }

    private fun parseDigits(buffer: ByteArray, decimal: SlowPathDecimal, currentIdx: Int): Int {
        var idx = currentIdx
        while (isDigit(buffer[idx])) {
            if (decimal.digitCount < SlowPathDecimal.MAX_DIGIT_COUNT) {
                decimal.digits[decimal.digitCount] = convertCharacterToDigit(buffer[idx])
            }
            decimal.digitCount++
            idx++
        }
        return idx
    }

    companion object {
        // When parsing doubles, we assume that a long used to store digits is unsigned. Thus, it can safely accommodate
        // up to 19 digits (9999999999999999999 < 2^64).
        private const val FAST_PATH_MAX_DIGIT_COUNT = 19

        // The smallest non-zero number representable in binary64 is 2^-1074, which is about 4.941 * 10^-324.
        // If we consider a number in the form of w * 10^q where 1 <= w <= 9999999999999999999, then
        // 1 * 10^q <= w * 10^q <= 9.999999999999999999 * 10^18 * 10^q. To ensure w * 10^q < 2^-1074, q must satisfy the
        // following inequality: 9.999999999999999999 * 10^(18 + q) < 2^-1074. This condition holds true whenever
        // 18 + q < -324. Thus, for q < -342, we can reliably conclude that the number w * 10^q is smaller than 2^-1074,
        // and this, in turn means the number is equal to zero.
        private const val FAST_PATH_MIN_POWER_OF_TEN = -342

        // We know that (1 - 2^-53) * 2^1024, which is about 1.798 * 10^308, is the largest number representable in binary64.
        // When the parsed number is expressed as w * 10^q, where w >= 1, we are sure that for any q > 308, the number is
        // infinite.
        private const val FAST_PATH_MAX_POWER_OF_TEN = 308
        private val POWERS_OF_TEN = doubleArrayOf(
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
        )
        private const val MAX_LONG_REPRESENTED_AS_DOUBLE_EXACTLY = (1L shl 53) - 1
        private const val IEEE64_EXPONENT_BIAS = 1023
        private const val IEEE64_SIGN_BIT_INDEX = 63
        private const val IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT = 52
        private const val IEEE64_SIGNIFICAND_SIZE_IN_BITS = IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT + 1
        private const val IEEE64_MAX_FINITE_NUMBER_EXPONENT = 1023
        private const val IEEE64_MIN_FINITE_NUMBER_EXPONENT = -1022
        private const val IEEE64_SUBNORMAL_EXPONENT = -1023
        private const val SLOW_PATH_MAX_SHIFT = 60
        private val SLOW_PATH_SHIFTS = byteArrayOf(
            0, 3, 6, 9, 13, 16, 19, 23, 26, 29,
            33, 36, 39, 43, 46, 49, 53, 56, 59,
        )
        private val MULTIPLICATION_MASK = -1L ushr (IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT + 3)

        private fun shouldBeHandledBySlowPath(buffer: ByteArray, startDigitsIdx: Int, digitCount: Int): Boolean {
            if (digitCount <= FAST_PATH_MAX_DIGIT_COUNT) {
                return false
            }
            var start = startDigitsIdx
            while (buffer[start] == '0'.code.toByte() || buffer[start] == '.'.code.toByte()) {
                start++
            }
            val significantDigitCount = digitCount - (start - startDigitsIdx)
            return significantDigitCount > FAST_PATH_MAX_DIGIT_COUNT
        }

        private fun computeDouble(negative: Boolean, significand10: Long, exp10: Long): Double {
            @Suppress("NAME_SHADOWING")
            var significand10 = significand10
            if (abs(exp10) < POWERS_OF_TEN.size && java.lang.Long.compareUnsigned(
                    significand10,
                    MAX_LONG_REPRESENTED_AS_DOUBLE_EXACTLY
                ) <= 0
            ) {
                // This path has been described in https://www.exploringbinary.com/fast-path-decimal-to-floating-point-conversion/.
                var result = significand10.toDouble()
                if (exp10 < 0) {
                    result /= POWERS_OF_TEN[(-exp10).toInt()]
                } else {
                    result *= POWERS_OF_TEN[exp10.toInt()]
                }
                return if (negative) -result else result
            }

            // The following path is an implementation of the Eisel-Lemire algorithm described by Daniel Lemire in
            // "Number Parsing at a Gigabyte per Second" (https://arxiv.org/abs/2101.11408).

            if (exp10 < FAST_PATH_MIN_POWER_OF_TEN || significand10 == 0L) {
                return zero(negative)
            } else if (exp10 > FAST_PATH_MAX_POWER_OF_TEN) {
                return infinity(negative)
            }

            // We start by normalizing the decimal significand so that it is within the range of [2^63, 2^64).
            val lz = java.lang.Long.numberOfLeadingZeros(significand10)
            significand10 = significand10 shl lz

            // Initially, the number we are parsing is in the form of w * 10^q = w * 5^q * 2^q, and our objective is to
            // convert it to m * 2^p. We can represent w * 10^q as w * 5^q * 2^r * 2^p, where w * 5^q * 2^r = m.
            // Therefore, in the next step we compute w * 5^q. The implementation of this multiplication is optimized
            // to minimize necessary operations while ensuring precise results. For more information, refer to the
            // aforementioned paper.
            val powersOfFiveTableIndex = 2 * (exp10 - NumberParserTables.MIN_POWER_OF_FIVE).toInt()
            var upper = java.lang.Math.unsignedMultiplyHigh(
                significand10,
                NumberParserTables.POWERS_OF_FIVE[powersOfFiveTableIndex]
            )
            var lower = significand10 * NumberParserTables.POWERS_OF_FIVE[powersOfFiveTableIndex]
            if ((upper and MULTIPLICATION_MASK) == MULTIPLICATION_MASK) {
                val secondUpper = java.lang.Math.unsignedMultiplyHigh(
                    significand10,
                    NumberParserTables.POWERS_OF_FIVE[powersOfFiveTableIndex + 1]
                )
                lower += secondUpper
                if (java.lang.Long.compareUnsigned(secondUpper, lower) > 0) {
                    upper++
                }
                // As it has been proven by Noble Mushtak and Daniel Lemire in "Fast Number Parsing Without Fallback"
                // (https://arxiv.org/abs/2212.06644), at this point we are sure that the product is sufficiently accurate,
                // and more computation is not needed.
            }

            // Here, we extract the binary significand from the product. Although in binary64 the significand has 53 bits,
            // we extract 54 bits to use the least significant bit for rounding. Since both the decimal significand and the
            // values stored in POWERS_OF_FIVE are normalized, ensuring that their most significant bits are set, the product
            // has either 0 or 1 leading zeros. As a result, we need to perform a right shift of either 9 or 10 bits.
            val upperBit = upper ushr 63
            val upperShift = upperBit + 9
            var significand2 = upper ushr upperShift.toInt()

            // Now, we have to determine the value of the binary exponent. Let's begin by calculating the contribution of
            // 10^q. Our goal is to compute f0 and f1 such that:
            // - when q >= 0: 10^q = (5^q / 2^(f0 - q)) * 2^f0
            // - when q < 0: 10^q = (2^(f1 - q) / 5^-q) * 2^f1
            // Both (5^q / 2^(f0 - q)) and (2^(f1 - q) / 5^-q) must fall within the range of [1, 2).
            // It turns out that these conditions are met when:
            // - 0 <= q <= FAST_PATH_MAX_POWER_OF_TEN, and f0 = floor(log2(5^q)) + q = floor(q * log(5) / log(2)) + q = (217706 * q) / 2^16.
            // - FAST_PATH_MIN_POWER_OF_TEN <= q < 0, and f1 = -ceil(log2(5^-q)) + q = -ceil(-q * log(5) / log(2)) + q = (217706 * q) / 2^16.
            // Thus, we can express the contribution of 10^q to the exponent as (217706 * exp10) >> 16.
            //
            // Furthermore, we need to factor in the following normalizations we've performed:
            // - shifting the decimal significand left bitwise
            // - shifting the binary significand right bitwise if the most significant bit of the product was 1
            // Therefore, we add (63 - lz + upperBit) to the exponent.
            var exp2 = ((217706 * exp10) shr 16) + 63 - lz + upperBit
            if (exp2 < IEEE64_MIN_FINITE_NUMBER_EXPONENT) {
                // In the next step, we right-shift the binary significand by the difference between the minimum exponent
                // and the binary exponent. In Java, the shift distance is limited to the range of 0 to 63, inclusive.
                // Thus, we need to handle the case when the distance is >= 64 separately and always return zero.
                if (exp2 <= IEEE64_MIN_FINITE_NUMBER_EXPONENT - 64) {
                    return zero(negative)
                }

                // In this branch, it is likely that we are handling a subnormal number. Therefore, we adjust the significand
                // to conform to the formula representing subnormal numbers: (significand2 * 2^(1 - IEEE64_EXPONENT_BIAS)) / 2^52.
                significand2 = significand2 shr (1 - IEEE64_EXPONENT_BIAS - exp2).toInt()
                // Round up if the significand is odd and remove the least significant bit that we've left for rounding.
                significand2 += significand2 and 1
                significand2 = significand2 shr 1

                // Here, we are addressing a scenario in which the original number was subnormal, but it became normal after
                // rounding up.
                exp2 =
                    if (significand2 < (1L shl 52)) IEEE64_SUBNORMAL_EXPONENT.toLong() else IEEE64_MIN_FINITE_NUMBER_EXPONENT.toLong()
                return toDouble(negative, significand2, exp2)
            }

            // Here, we are addressing a scenario of rounding the binary significand when it falls precisely halfway
            // between two integers. To understand the rationale behind the conditions used to identify this case, refer to
            // sections 6, 8.1, and 9.1 of "Number Parsing at a Gigabyte per Second".
            if (exp10 >= -4 && exp10 <= 23) {
                if ((significand2 shl upperShift.toInt() == upper) && (java.lang.Long.compareUnsigned(lower, 1) <= 0)) {
                    if ((significand2 and 3) == 1L) {
                        significand2 = significand2 and 1L.inv()
                    }
                }
            }

            // Round up if the significand is odd and remove the least significant bit that we've left for rounding.
            significand2 += significand2 and 1
            significand2 = significand2 shr 1

            if (significand2 == (1L shl IEEE64_SIGNIFICAND_SIZE_IN_BITS)) {
                // If we've reached here, it means that rounding has caused an overflow. We need to divide the significand
                // by 2 and update the exponent accordingly.
                significand2 = significand2 shr 1
                exp2++
            }

            if (exp2 > IEEE64_MAX_FINITE_NUMBER_EXPONENT) {
                return infinity(negative)
            }
            return toDouble(negative, significand2, exp2)
        }

        private fun toDouble(negative: Boolean, significand2: Long, exp2: Long): Double {
            var bits = significand2
            bits = bits and (1L shl IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT).inv() // clear the implicit bit
            bits = bits or ((exp2 + IEEE64_EXPONENT_BIAS) shl IEEE64_SIGNIFICAND_EXPLICIT_BIT_COUNT)
            bits = if (negative) (bits or (1L shl IEEE64_SIGN_BIT_INDEX)) else bits
            return Double.fromBits(bits)
        }

        private fun infinity(negative: Boolean): Double =
            if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY

        private fun zero(negative: Boolean): Double =
            if (negative) -0.0 else 0.0

        private fun resolveShiftDistanceBasedOnExponent10(exp10: Int): Int =
            if (exp10 < SLOW_PATH_SHIFTS.size) SLOW_PATH_SHIFTS[exp10].toInt() else SLOW_PATH_MAX_SHIFT

        private fun convertCharacterToDigit(b: Byte): Byte =
            (b.toInt() - '0'.code).toByte()

        private fun isDigit(b: Byte): Boolean =
            b >= '0'.code.toByte() && b <= '9'.code.toByte()
    }
}
