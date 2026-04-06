package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class NumberParsingTest {

    // --- Long ---

    @Test
    fun `parse long - positive`() {
        val parser = SimdJsonParser()
        val num = parser.parse("123").shouldBeInstanceOf<JsonNumber>()
        num.toLong() shouldBe 123L
    }

    @Test
    fun `parse long - negative`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-456").shouldBeInstanceOf<JsonNumber>()
        num.toLong() shouldBe -456L
    }

    @Test
    fun `parse long - zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("0").shouldBeInstanceOf<JsonNumber>()
        num.toLong() shouldBe 0L
    }

    @Test
    fun `parse long - MAX_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("${Long.MAX_VALUE}").shouldBeInstanceOf<JsonNumber>()
        num.isLong shouldBe true
        num.toLong() shouldBe Long.MAX_VALUE
    }

    @Test
    fun `parse long - MIN_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("${Long.MIN_VALUE}").shouldBeInstanceOf<JsonNumber>()
        num.isLong shouldBe true
        num.toLong() shouldBe Long.MIN_VALUE
    }

    @Test
    fun `parse long - single digits`() {
        val parser = SimdJsonParser()
        for (d in 0..9) {
            withClue("Digit $d") {
                val num = parser.parse("$d").shouldBeInstanceOf<JsonNumber>()
                num.toLong() shouldBe d.toLong()
            }
        }
    }

    @Test
    fun `parse long - values exceeding Long range are unsigned`() {
        val parser = SimdJsonParser()
        // 9223372036854775808 = Long.MAX_VALUE + 1 — parses as unsigned
        val num = parser.parse("9223372036854775808").shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.isLong shouldBe false
        // toLong() should throw since value exceeds Long range
        shouldThrow<JsonParsingException> { num.toLong() }
        // toULong() should work
        num.toULong() shouldBe 9223372036854775808uL
    }

    // --- Int ---

    @Test
    fun `parse int - positive`() {
        val parser = SimdJsonParser()
        val num = parser.parse("123").shouldBeInstanceOf<JsonNumber>()
        num.toInt() shouldBe 123
    }

    @Test
    fun `parse int - negative`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-456").shouldBeInstanceOf<JsonNumber>()
        num.toInt() shouldBe -456
    }

    @Test
    fun `parse int - zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("0").shouldBeInstanceOf<JsonNumber>()
        num.toInt() shouldBe 0
    }

    @Test
    fun `parse int - MAX_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("2147483647").shouldBeInstanceOf<JsonNumber>()
        num.toInt() shouldBe Int.MAX_VALUE
    }

    @Test
    fun `parse int - MIN_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-2147483648").shouldBeInstanceOf<JsonNumber>()
        num.toInt() shouldBe Int.MIN_VALUE
    }

    @Test
    fun `parse int - positive overflow throws`() {
        val parser = SimdJsonParser()
        val num = parser.parse("2147483648").shouldBeInstanceOf<JsonNumber>()
        shouldThrow<JsonParsingException> { num.toInt() }
    }

    @Test
    fun `parse int - negative overflow throws`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-2147483649").shouldBeInstanceOf<JsonNumber>()
        shouldThrow<JsonParsingException> { num.toInt() }
    }

    // --- ULong ---

    @Test
    fun `parse ulong - positive`() {
        val parser = SimdJsonParser()
        val num = parser.parse("123").shouldBeInstanceOf<JsonNumber>()
        num.toULong() shouldBe 123uL
    }

    @Test
    fun `parse ulong - zero`() {
        val parser = SimdJsonParser()
        val num = parser.parse("0").shouldBeInstanceOf<JsonNumber>()
        num.toULong() shouldBe 0uL
    }

    @Test
    fun `parse ulong - MAX_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("18446744073709551615").shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.toULong() shouldBe ULong.MAX_VALUE
    }

    @Test
    fun `parse ulong - just below max`() {
        val parser = SimdJsonParser()
        val num = parser.parse("18446744073709551614").shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.toULong() shouldBe ULong.MAX_VALUE - 1uL
    }

    @Test
    fun `parse ulong - Long MAX_VALUE boundary`() {
        val parser = SimdJsonParser()
        val num = parser.parse("9223372036854775807").shouldBeInstanceOf<JsonNumber>()
        num.toULong() shouldBe 9223372036854775807uL
    }

    @Test
    fun `parse ulong - just above Long MAX_VALUE`() {
        val parser = SimdJsonParser()
        val num = parser.parse("9223372036854775808").shouldBeInstanceOf<JsonNumber>()
        num.toULong() shouldBe 9223372036854775808uL
    }

    @Test
    fun `parse ulong - single digits`() {
        val parser = SimdJsonParser()
        for (d in 0..9) {
            withClue("Digit $d") {
                val num = parser.parse("$d").shouldBeInstanceOf<JsonNumber>()
                num.toULong() shouldBe d.toULong()
            }
        }
    }

    @Test
    fun `parse ulong - negative throws`() {
        val parser = SimdJsonParser()
        val num = parser.parse("-1").shouldBeInstanceOf<JsonNumber>()
        shouldThrow<JsonParsingException> { num.toULong() }
    }

    // --- Validation errors ---

    @Test
    fun `leading zeros throw`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("01") }
    }

    @Test
    fun `bare minus throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("-") }
    }

    @Test
    fun `trailing non-structural throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("123x") }
    }

    @Test
    fun `empty input throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("") }
    }

    // --- Overflow beyond ULong ---

    @Test
    fun `value exceeding ULong MAX throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("18446744073709551616") }
    }

    @Test
    fun `20 digits starting with 2 throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("20000000000000000000") }
    }

    @Test
    fun `21 digits throws`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("100000000000000000000") }
    }

    @Test
    fun `19 digit positive overflow parses as unsigned`() {
        val parser = SimdJsonParser()
        // 9999999999999999999 > Long.MAX_VALUE but < ULong.MAX_VALUE
        val num = parser.parse("9999999999999999999").shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.toULong() shouldBe 9999999999999999999uL
    }
}
