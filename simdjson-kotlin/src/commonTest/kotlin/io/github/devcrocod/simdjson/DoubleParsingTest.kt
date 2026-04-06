package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class DoubleParsingTest {

    /**
     * Asserts that parsing [input] either returns a double equal to [expected],
     * or throws [JsonParsingException].
     *
     * This is needed because the JVM implementation returns infinity/negative-infinity
     * for overflow, while the native C++ simdjson backend treats overflow as a parse error.
     */
    private fun assertDoubleOrParseError(parser: SimdJsonParser, input: String, expected: Double) {
        try {
            val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
            num.toDouble() shouldBe expected
        } catch (e: JsonParsingException) {
            // Native C++ simdjson rejects overflow — only allow this for infinity inputs
            if (!expected.isInfinite()) throw e
        }
    }

    // --- Basic values ---

    @Test
    fun `parse simple double`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1.0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1.0
    }

    @Test
    fun `parse negative double`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-3.14").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe -3.14
    }

    @Test
    fun `parse double with exponent`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1e10").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1e10
    }

    @Test
    fun `parse double with negative exponent`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1.5e-3").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1.5e-3
    }

    @Test
    fun `parse double with uppercase exponent`() {
        val parser = SimdJsonParser()
        val num = parser.parse("2.5E4").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 2.5E4
    }

    @Test
    fun `parse double with positive exponent sign`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1.0e+2").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 100.0
    }

    @Test
    fun `parse zero point five`() {
        val parser = SimdJsonParser()
        val num = parser.parse("0.5").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 0.5
    }

    @Test
    fun `parse zero point zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("0.0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 0.0
    }

    @Test
    fun `parse negative zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-0.0").shouldBeInstanceOf<JsonNumber>()
        val result = num.toDouble()
        result shouldBe -0.0
        (1.0 / result == Double.NEGATIVE_INFINITY) shouldBe true
    }

    // --- Fast path (exactly representable) ---

    @Test
    fun `fast path - small integer`() {
        val parser = SimdJsonParser()
        val num = parser.parse("42.0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 42.0
    }

    @Test
    fun `fast path - power of ten`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1e15").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1e15
    }

    // --- Eisel-Lemire path ---

    @Test
    fun `Eisel-Lemire path`() {
        val parser = SimdJsonParser()
        val num = parser.parse("7.0560899624998275e18").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 7.0560899624998275e18
    }

    // --- Slow path (>19 significant digits) ---

    @Test
    fun `slow path - many digits`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1.00000000000000000001e0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1.0
    }

    @Test
    fun `slow path - large significand`() {
        val parser = SimdJsonParser()
        val num = parser.parse("12345678901234567890.0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 12345678901234567890.0
    }

    // --- Edge cases: infinity ---

    @Test
    fun `parse Double MAX_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1.7976931348623157e308").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe Double.MAX_VALUE
    }

    @Test
    fun `overflow to positive infinity`() {
        val parser = SimdJsonParser()
        // JVM returns +Infinity, native C++ simdjson throws JsonParsingException
        assertDoubleOrParseError(parser, "1e309", Double.POSITIVE_INFINITY)
    }

    @Test
    fun `overflow to negative infinity`() {
        val parser = SimdJsonParser()
        // JVM returns -Infinity, native C++ simdjson throws JsonParsingException
        assertDoubleOrParseError(parser, "-1e309", Double.NEGATIVE_INFINITY)
    }

    // --- Edge cases: zero ---

    @Test
    fun `underflow to zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1e-400").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 0.0
    }

    @Test
    fun `underflow to negative zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-1e-400").shouldBeInstanceOf<JsonNumber>()
        val result = num.toDouble()
        result shouldBe -0.0
        (1.0 / result == Double.NEGATIVE_INFINITY) shouldBe true
    }

    // --- Edge cases: subnormal ---

    @Test
    fun `parse Double MIN_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("5e-324").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 5e-324
    }

    @Test
    fun `parse smallest normal double`() {
        val parser = SimdJsonParser()
        val num = parser.parse("2.2250738585072014e-308").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 2.2250738585072014e-308
    }

    @Test
    fun `subnormal rounding to normal`() {
        val parser = SimdJsonParser()
        val num = parser.parse("2.2250738585072013e-308").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 2.2250738585072013e-308
    }

    // --- Validation errors ---

    @Test
    fun `leading zeros throw`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("01.0") }
    }

    @Test
    fun `decimal point without digit throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("1.") }
    }

    @Test
    fun `minus only throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("-.") }
    }

    @Test
    fun `trailing non-structural character throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("1.0x") }
    }

    @Test
    fun `missing digit after exponent throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("1e") }
    }

    // --- Specific rounding cases ---

    @Test
    fun `exact halfway rounding`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1.00000000000000015").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1.0000000000000002
    }

    @Test
    fun `exponent only`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1e0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1.0
    }

    // --- Min normal double (boundary between subnormal and normal) ---

    @Test
    fun `min normal double representations`() {
        val parser = SimdJsonParser()
        val expected = Double.fromBits(0x0010000000000000L) // 0x1p-1022
        val representations = listOf(
            "2.2250738585072016e-308",
            "2.2250738585072015e-308",
            "2.2250738585072014e-308",
            "2.2250738585072013e-308",
            "2.2250738585072012e-308",
        )
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    // --- Max subnormal double ---

    @Test
    fun `max subnormal double representations`() {
        val parser = SimdJsonParser()
        val expected = Double.fromBits(0x000FFFFFFFFFFFFFL) // 0x0.fffffffffffffp-1022
        val representations = listOf(
            "2.2250738585072011e-308",
            "2.2250738585072010e-308",
            "2.2250738585072009e-308",
            "2.2250738585072008e-308",
            "2.2250738585072007e-308",
        )
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    @Test
    fun `max subnormal double - long decimal representation`() {
        val parser = SimdJsonParser()
        val expected = Double.fromBits(0x000FFFFFFFFFFFFFL)
        val input =
            "0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022250738585072008890245868760858598876504231122409594654935248025624400092282356951787758888037591552642309780950434312085877387158357291821993020294379224223559819827501242041788969571311791082261043971979604000454897391938079198936081525613113376149842043271751033627391549782731594143828136275113838604094249464942286316695429105080201815926642134996606517803095075913058719846423906068637102005108723282784678843631944515866135041223479014792369585208321597621066375401613736583044193603714778355306682834535634005074073040135602968046375918583163124224521599262546494300836851861719422417646455137135420132217031370496583210154654068035397417906022589503023501937519773030945763173210852507299305089761582519159720757232455434770912461317493580281734466552734375"
        val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe expected
    }

    // --- Min subnormal double ---

    @Test
    fun `min subnormal double representations`() {
        val parser = SimdJsonParser()
        val expected = Double.fromBits(0x0000000000000001L)
        val representations = listOf(
            "3e-324",
            "4.9e-324",
            "4.9406564584124654e-324",
            "4.94065645841246544176568792868e-324",
        )
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    // --- Max double ---

    @Test
    fun `max double representations`() {
        val parser = SimdJsonParser()
        val representations = listOf(
            "1.7976931348623157e308",
            "1.7976931348623158e308",
        )
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe Double.MAX_VALUE
            }
        }
    }

    // --- Positive infinity edge cases ---
    // Note: JVM returns +Infinity for overflow, native C++ simdjson throws JsonParsingException.

    @Test
    fun `positive infinity - various representations`() {
        val parser = SimdJsonParser()
        val representations = listOf(
            "1.9e308",
            "1.8e308",
            "1234456789012345678901234567890e9999999999999999999999999999",
            "1.832312213213213232132132143451234453123412321321312e308",
            "2139879401095466344511101915470454744.9813888656856943E+272",
            "2e30000000000000000",
            "2e3000",
            "1234456789012345678901234567890e999999999999999999999999999",
            "1.7976931348623159e308",
        )
        for (input in representations) {
            withClue("Input: $input") {
                assertDoubleOrParseError(parser, input, Double.POSITIVE_INFINITY)
            }
        }
    }

    // --- Negative infinity edge cases ---
    // Note: JVM returns -Infinity for overflow, native C++ simdjson throws JsonParsingException.

    @Test
    fun `negative infinity - various representations`() {
        val parser = SimdJsonParser()
        val representations = listOf(
            "-1.9e308",
            "-1.8e308",
            "-1234456789012345678901234567890e9999999999999999999999999999",
            "-1.832312213213213232132132143451234453123412321321312e308",
            "-2139879401095466344511101915470454744.9813888656856943E+272",
            "-2e30000000000000000",
            "-2e3000",
            "-1234456789012345678901234567890e999999999999999999999999999",
            "-1.7976931348623159e308",
        )
        for (input in representations) {
            withClue("Input: $input") {
                assertDoubleOrParseError(parser, input, Double.NEGATIVE_INFINITY)
            }
        }
    }

    // --- Positive zero edge cases ---

    @Test
    fun `positive zero - various representations`() {
        val parser = SimdJsonParser()
        val representations = listOf(
            "2251799813685248e-342",
            "9999999999999999999e-343",
            "1.23e-341",
            "123e-343",
            "0.0e-999",
            "0e9999999999999999999999999999",
            "18446744073709551615e-343",
            "0.099999999999999999999e-323",
            "0.99999999999999999999e-324",
            "0.9999999999999999999e-324",
        )
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe 0.0
            }
        }
    }

    // --- Negative zero edge cases ---

    @Test
    fun `negative zero - various representations`() {
        val parser = SimdJsonParser()
        val representations = listOf(
            "-2251799813685248e-342",
            "-9999999999999999999e-343",
            "-1.23e-341",
            "-123e-343",
            "-0.0e-999",
            "-0e9999999999999999999999999999",
            "-18446744073709551615e-343",
            "-0.099999999999999999999e-323",
            "-0.99999999999999999999e-324",
            "-0.9999999999999999999e-324",
        )
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                val result = num.toDouble()
                result shouldBe -0.0
                (1.0 / result == Double.NEGATIVE_INFINITY) shouldBe true
            }
        }
    }

    // --- Rounding overflow ---

    @Test
    fun `rounding overflow - significand rounds up to 2 pow 53`() {
        val parser = SimdJsonParser()
        val representations = listOf(
            "7.2057594037927933e16",
            "72057594037927933.0000000000000000",
        )
        val expected = 7.2057594037927936e16
        for (input in representations) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    // --- Round ties to even ---

    @Test
    fun `round ties to even`() {
        val parser = SimdJsonParser()
        val cases = listOf(
            "2251799813685803.75" to 2251799813685804.0,
            "4503599627370497.5" to 4503599627370498.0,
            "4503599627475353.5" to 4503599627475354.0,
            "9007199254740993.0" to 9007199254740992.0,
            "4503599627370496.5" to 4503599627370496.0,
            "4503599627475352.5" to 4503599627475352.0,
            "2251799813685248.25" to 2251799813685248.0,
            "1125899906842624.125" to 1125899906842624.0,
            "1125899906842901.875" to 1125899906842902.0,
        )
        for ((input, expected) in cases) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    // --- Round up to nearest ---

    @Test
    fun `round up to nearest`() {
        val parser = SimdJsonParser()
        val cases = listOf(
            "12045e23" to 12045e23,
            "2251799813685803.85" to 2251799813685804.0,
            "4503599627370497.6" to 4503599627370498.0,
            "45035996.273704995" to 45035996.273705,
            "4503599627475353.6" to 4503599627475354.0,
            "1.797693134862315700000000000000001e308" to 1.7976931348623157e308,
            "860228122.6654514319E+90" to 8.602281226654515e98,
            "-42823146028335318693e-128" to -4.282314602833532e-109,
            "-2402844368454405395.2" to -2402844368454405600.0,
            "2402844368454405395.2" to 2402844368454405600.0,
            "-2240084132271013504.131248280843119943687942846658579428" to -2240084132271013600.0,
        )
        for ((input, expected) in cases) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    // --- Round down to nearest ---

    @Test
    fun `round down to nearest`() {
        val parser = SimdJsonParser()
        val cases = listOf(
            "2251799813685803.15" to 2251799813685803.0,
            "4503599627370497.2" to 4503599627370497.0,
            "45035996.273704985" to 45035996.27370498,
            "4503599627475353.2" to 4503599627475353.0,
            "1.0000000000000006661338147750939242541790008544921875" to 1.0000000000000007,
            "-92666518056446206563e3" to -9.26665180564462e22,
            "90054602635948575728e72" to 9.005460263594858e91,
            "7.0420557077594588669468784357561207962098443483187940792729600000e59" to 7.042055707759459e59,
        )
        for ((input, expected) in cases) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    // --- Exact double ---

    @Test
    fun `exact double - max safe integer`() {
        val parser = SimdJsonParser()
        val num = parser.parse("9007199254740991.0").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 9007199254740991.0
    }

    // --- Exponent edge cases ---

    @Test
    fun `exponent with leading zeros`() {
        val parser = SimdJsonParser()
        val cases = listOf(
            "1e000000000000000000001" to 10.0,
            "1e-000000000000000000001" to 0.1,
        )
        for ((input, expected) in cases) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    @Test
    fun `exponent with more digits than long can accommodate - underflow to zero`() {
        val parser = SimdJsonParser()
        val cases = listOf(
            "0e999999999999999999999" to 0.0,
            "0e-999999999999999999999" to 0.0,
            "1e-999999999999999999999" to 0.0,
            "9999999999999999999999999999999999999999e-999999999999999999999" to 0.0,
        )
        for ((input, expected) in cases) {
            withClue("Input: $input") {
                val num = parser.parse(input).shouldBeInstanceOf<JsonNumber>()
                num.toDouble() shouldBe expected
            }
        }
    }

    @Test
    fun `exponent with more digits than long can accommodate - overflow to infinity`() {
        val parser = SimdJsonParser()
        // JVM returns +Infinity, native C++ simdjson throws JsonParsingException
        val cases = listOf(
            "0.9999999999999999999999999999999999999999e999999999999999999999" to Double.POSITIVE_INFINITY,
        )
        for ((input, expected) in cases) {
            withClue("Input: $input") {
                assertDoubleOrParseError(parser, input, expected)
            }
        }
    }

    // --- Subnormal boundary from C++ tests ---

    @Test
    fun `subnormal boundary - 2 pow minus 1074`() {
        val parser = SimdJsonParser()
        val num = parser.parse("5e-324").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe Double.MIN_VALUE
    }

    @Test
    fun `subnormal - values near zero boundary`() {
        val parser = SimdJsonParser()
        val num = parser.parse("2.4e-324").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 0.0
    }

    // --- From FloatParserTest (adapted as Double) ---

    @Test
    fun `float range - MAX_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("3.4028235e38").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 3.4028235e38
    }

    @Test
    fun `float range - overflow to infinity`() {
        val parser = SimdJsonParser()
        val num = parser.parse("3.5e38").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 3.5e38
    }

    @Test
    fun `float range - smallest normal`() {
        val parser = SimdJsonParser()
        val expected = Float.fromBits(0x00800000).toDouble()
        val num = parser.parse("1.1754943508222875e-38").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe expected
    }

    @Test
    fun `float range - max subnormal`() {
        val parser = SimdJsonParser()
        val expected = Float.fromBits(0x007FFFFF).toDouble()
        val num = parser.parse("1.1754942106924411e-38").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe expected
    }

    // --- From ExponentParserTest (behavioral only) ---

    @Test
    fun `exponent - leading zeros in exponent`() {
        val parser = SimdJsonParser()
        val num = parser.parse("1e007").shouldBeInstanceOf<JsonNumber>()
        num.toDouble() shouldBe 1e7
    }

    @Test
    fun `exponent - huge exponent overflows to infinity`() {
        val parser = SimdJsonParser()
        // JVM returns +Infinity, native C++ simdjson throws JsonParsingException
        assertDoubleOrParseError(parser, "1e999999999999999999999", Double.POSITIVE_INFINITY)
    }
}
