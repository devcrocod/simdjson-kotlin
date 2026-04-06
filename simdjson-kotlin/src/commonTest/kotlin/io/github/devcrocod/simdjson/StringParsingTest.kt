package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class StringParsingTest {

    // --- KMP helpers ---

    private fun Int.toHex4(): String =
        this.toString(16).padStart(4, '0').uppercase()

    private fun codePointToString(cp: Int): String =
        if (cp < 0x10000) {
            Char(cp).toString()
        } else {
            val high = ((cp - 0x10000) shr 10) + 0xD800
            val low = ((cp - 0x10000) and 0x3FF) + 0xDC00
            charArrayOf(high.toChar(), low.toChar()).concatToString()
        }

    private fun highSurrogate(cp: Int): Int =
        ((cp - 0x10000) shr 10) + 0xD800

    private fun lowSurrogate(cp: Int): Int =
        ((cp - 0x10000) and 0x3FF) + 0xDC00

    private fun parseString(json: String): String =
        SimdJsonParser().parse(json).shouldBeInstanceOf<JsonString>().value

    // --- Basic strings ---

    @Test
    fun `simple string`() {
        parseString("\"abc\"") shouldBe "abc"
    }

    @Test
    fun `empty string`() {
        parseString("\"\"") shouldBe ""
    }

    @Test
    fun `long string spanning multiple vector chunks`() {
        val str = "a".repeat(100)
        parseString("\"$str\"") shouldBe str
    }

    @Test
    fun `escape in middle of long string`() {
        val prefix = "a".repeat(30)
        val suffix = "b".repeat(30)
        parseString("\"${prefix}\\n${suffix}\"") shouldBe "${prefix}\n${suffix}"
    }

    // --- Escape sequences ---

    @Test
    fun `escape quote`() {
        parseString("\"a\\\"b\"") shouldBe "a\"b"
    }

    @Test
    fun `escape backslash`() {
        parseString("\"a\\\\b\"") shouldBe "a\\b"
    }

    @Test
    fun `escape forward slash`() {
        parseString("\"a\\/b\"") shouldBe "a/b"
    }

    @Test
    fun `escape backspace`() {
        parseString("\"a\\bb\"") shouldBe "a\bb"
    }

    @Test
    fun `escape form feed`() {
        parseString("\"a\\fb\"") shouldBe "a\u000Cb"
    }

    @Test
    fun `escape newline`() {
        parseString("\"a\\nb\"") shouldBe "a\nb"
    }

    @Test
    fun `escape carriage return`() {
        parseString("\"a\\rb\"") shouldBe "a\rb"
    }

    @Test
    fun `escape tab`() {
        parseString("\"a\\tb\"") shouldBe "a\tb"
    }

    @Test
    fun `multiple escapes`() {
        parseString("\"a\\tb\\nc\"") shouldBe "a\tb\nc"
    }

    @Test
    fun `all JSON escape sequences`() {
        val escapes = mapOf(
            "\\\"" to "\"",
            "\\\\" to "\\",
            "\\/" to "/",
            "\\b" to "\b",
            "\\f" to "\u000C",
            "\\n" to "\n",
            "\\r" to "\r",
            "\\t" to "\t",
        )
        for ((jsonEscape, expected) in escapes) {
            val input = "\"a${jsonEscape}b\""
            withClue("Escape: $jsonEscape") {
                parseString(input) shouldBe "a${expected}b"
            }
        }
    }

    // --- Unicode escapes ---

    @Test
    fun `unicode escape - ASCII`() {
        // \u0041 = 'A'
        parseString("\"\\u0041\"") shouldBe "A"
    }

    @Test
    fun `unicode escape - two byte`() {
        // \u00e9 = 'e' (2-byte UTF-8)
        parseString("\"\\u00e9\"") shouldBe "\u00e9"
    }

    @Test
    fun `unicode escape - three byte`() {
        // \u4e16 = '世' (3-byte UTF-8)
        parseString("\"\\u4e16\"") shouldBe "\u4e16"
    }

    // --- Surrogate pairs ---

    @Test
    fun `surrogate pair - emoji`() {
        // \uD83D\uDE00 = U+1F600
        parseString("\"\\uD83D\\uDE00\"") shouldBe "\uD83D\uDE00"
    }

    @Test
    fun `surrogate pair - musical symbol`() {
        // \uD834\uDD1E = U+1D11E (musical symbol G clef)
        parseString("\"\\uD834\\uDD1E\"") shouldBe "\uD834\uDD1E"
    }

    @Test
    fun `string with multiple surrogate pairs`() {
        // Two emoji: U+1F600, U+1F3B5
        parseString("\"\\uD83D\\uDE00\\uD83C\\uDFB5\"") shouldBe "\uD83D\uDE00\uD83C\uDFB5"
    }

    // --- Comprehensive Unicode escape tests ---

    @Test
    fun `unicode escape - all ASCII code points`() {
        // U+0020 to U+007E (printable ASCII, excluding " and \)
        for (cp in 0x0020..0x007E) {
            if (cp == '"'.code || cp == '\\'.code) continue
            val hex = cp.toHex4()
            val input = "\"\\u$hex\""
            withClue("Code point U+$hex") {
                parseString(input) shouldBe codePointToString(cp)
            }
        }
    }

    @Test
    fun `unicode escape - two byte UTF-8 range`() {
        // Sample from U+0080 to U+07FF (2-byte UTF-8)
        val codePoints = listOf(0x0080, 0x00FF, 0x0100, 0x017E, 0x0400, 0x04FF, 0x07FF)
        for (cp in codePoints) {
            val hex = cp.toHex4()
            val input = "\"\\u$hex\""
            withClue("Code point U+$hex") {
                parseString(input) shouldBe codePointToString(cp)
            }
        }
    }

    @Test
    fun `unicode escape - three byte UTF-8 range`() {
        // Sample from U+0800 to U+FFFF (3-byte UTF-8), excluding surrogates (D800-DFFF)
        val codePoints = listOf(0x0800, 0x1000, 0x2000, 0x4E16, 0xAC00, 0xD7FF, 0xE000, 0xFFFD)
        for (cp in codePoints) {
            val hex = cp.toHex4()
            val input = "\"\\u$hex\""
            withClue("Code point U+$hex") {
                parseString(input) shouldBe codePointToString(cp)
            }
        }
    }

    @Test
    fun `unicode escape - supplementary plane via surrogate pairs`() {
        // U+10000 to U+10FFFF (4-byte UTF-8, requires surrogate pairs in JSON)
        val codePoints = listOf(0x10000, 0x1F600, 0x1F4A9, 0x1D11E, 0x20000, 0x2F800, 0x10FFFF)
        for (cp in codePoints) {
            val high = highSurrogate(cp)
            val low = lowSurrogate(cp)
            val input = "\"\\u${high.toHex4()}\\u${low.toHex4()}\""
            withClue("Code point U+${cp.toString(16).uppercase()}") {
                parseString(input) shouldBe codePointToString(cp)
            }
        }
    }

    @Test
    fun `unicode escape - all BMP non-surrogate code points`() {
        // Comprehensive: test every BMP code point except surrogates and U+0000 (null byte)
        val parser = SimdJsonParser()
        for (cp in 0x0001..0xFFFF) {
            if (cp in 0xD800..0xDFFF) continue
            val hex = cp.toHex4()
            val input = "\"\\u$hex\""
            withClue("Code point U+$hex") {
                val result = parser.parse(input).shouldBeInstanceOf<JsonString>().value
                result shouldBe codePointToString(cp)
            }
        }
    }

    // --- Combined/complex escape cases ---

    @Test
    fun `unicode escape followed by regular escape`() {
        parseString("\"\\u0041\\n\"") shouldBe "A\n"
    }

    @Test
    fun `multiple unicode escapes`() {
        parseString("\"\\u0048\\u0065\\u006C\\u006C\\u006F\"") shouldBe "Hello"
    }

    @Test
    fun `mixed literal and escaped content`() {
        parseString("\"Hello\\tWorld\\n\\u0041\\u0042\"") shouldBe "Hello\tWorld\nAB"
    }

    @Test
    fun `unicode escape at vector boundary`() {
        // Place a unicode escape right at the SIMD vector chunk boundary
        val prefix = "a".repeat(29) // 29 + opening quote = 30 bytes, close to 32-byte vector
        parseString("\"${prefix}\\u0041\"") shouldBe "${prefix}A"
    }

    @Test
    fun `surrogate pair at vector boundary`() {
        val prefix = "a".repeat(26) // close to vector boundary
        parseString("\"${prefix}\\uD83D\\uDE00\"") shouldBe "${prefix}\uD83D\uDE00"
    }

    @Test
    fun `case insensitive hex digits in unicode escape`() {
        parseString("\"\\u004F\"") shouldBe "O"
        parseString("\"\\u004f\"") shouldBe "O"
        parseString("\"\\u00e9\"") shouldBe "\u00e9"
        parseString("\"\\u00E9\"") shouldBe "\u00e9"
        parseString("\"\\uAbCd\"") shouldBe "\uABCD"
    }

    // --- Error cases ---

    @Test
    fun `invalid escape character throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("[\"\\g\"]") }
    }

    @Test
    fun `lone low surrogate throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("\"\\uDC00\"") }
    }

    @Test
    fun `high surrogate without low surrogate throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("\"\\uD800abc\"") }
    }

    @Test
    fun `invalid low surrogate range throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("\"\\uD800\\u0041\"") }
    }

    @Test
    fun `incomplete unicode escape throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("\"\\u00\"") }
    }

    // --- Comprehensive surrogate pair error tests ---

    @Test
    fun `lone low surrogate - samples from DC00-DFFF range`() {
        val parser = SimdJsonParser()
        val samples = listOf(0xDC00, 0xDC01, 0xDD00, 0xDE00, 0xDEFF, 0xDF00, 0xDFFF)
        for (cp in samples) {
            val hex = cp.toHex4()
            val input = "\"\\u$hex\""
            withClue("Lone low surrogate U+$hex") {
                shouldThrow<JsonParsingException> { parser.parse(input) }
            }
        }
    }

    @Test
    fun `high surrogate with invalid low surrogate escape formats`() {
        val parser = SimdJsonParser()
        val invalidInputs = listOf(
            "\\uD8001",      // digit instead of \u
            "\\uD800\\1",    // \1 instead of \uXXXX
            "\\uD800u",      // u without backslash
            "\\uD800\\e",    // \e is not a valid escape
            "\\uD800\\DC00", // \DC00 without u prefix
            "\\uD800",       // high surrogate at end of string
        )
        for (input in invalidInputs) {
            withClue("Input: $input") {
                shouldThrow<JsonParsingException> { parser.parse("\"$input\"") }
            }
        }
    }

    @Test
    fun `high surrogate followed by invalid low surrogate value`() {
        val parser = SimdJsonParser()
        // \uD800\uXXXX where XXXX is NOT in DC00-DFFF
        val invalidLows = listOf(0x0000, 0x0041, 0x0100, 0xD7FF, 0xD800, 0xDBFF, 0xE000, 0xFFFF)
        for (low in invalidLows) {
            val hex = low.toHex4()
            val input = "\\uD800\\u$hex"
            withClue("High surrogate \\uD800 + invalid low \\u$hex") {
                shouldThrow<JsonParsingException> { parser.parse("\"$input\"") }
            }
        }
    }

    @Test
    fun `high surrogate followed by missing low surrogate value`() {
        val parser = SimdJsonParser()
        // \uD800\u -- backslash-u but then string ends before 4 hex digits
        shouldThrow<JsonParsingException> { parser.parse("\"\\uD800\\u\"") }
    }

    // --- Incomplete unicode escapes ---

    @Test
    fun `incomplete unicode escapes of various lengths`() {
        val parser = SimdJsonParser()
        val incompleteInputs = listOf(
            "\\u",
            "\\u0",
            "\\u00",
            "\\u004",
        )
        for (input in incompleteInputs) {
            withClue("Input: $input") {
                shouldThrow<JsonParsingException> { parser.parse("\"$input\"") }
            }
        }
    }

    // --- Invalid escape characters ---

    @Test
    fun `invalid escape characters`() {
        val parser = SimdJsonParser()
        val invalidEscapes = listOf(
            'a', 'c', 'd', 'e', 'g', 'h', 'i', 'j', 'k', 'l',
            'm', 'o', 'p', 'q', 's', 'v', 'w', 'x', 'y', 'z',
            'A', '0', '1', '!', '@', '#'
        )
        for (ch in invalidEscapes) {
            withClue("Invalid escape: \\$ch") {
                shouldThrow<JsonParsingException> { parser.parse("[\"\\$ch\"]") }
            }
        }
    }
}
