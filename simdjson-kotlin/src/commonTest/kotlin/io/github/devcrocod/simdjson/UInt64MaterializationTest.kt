package io.github.devcrocod.simdjson

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class UInt64MaterializationTest {

    @Test
    fun domParseLargeUnsignedInteger() {
        val parser = SimdJsonParser()
        val num = parser.parse("9999999999999999999").shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.isInteger shouldBe true
        num.isLong shouldBe false
        num.toULong() shouldBe 9999999999999999999u
        parser.close()
    }

    @Test
    fun domParseUlongMaxBoundary() {
        val parser = SimdJsonParser()
        // Long.MAX_VALUE + 1 = 9223372036854775808 — should be UINT64
        val num = parser.parse("9223372036854775808").shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.toULong() shouldBe 9223372036854775808u
        parser.close()
    }

    @Test
    fun domParseRegularLongStaysLong() {
        val parser = SimdJsonParser()
        val num = parser.parse("42").shouldBeInstanceOf<JsonNumber>()
        num.isLong shouldBe true
        num.isUnsigned shouldBe false
        num.toLong() shouldBe 42L
        parser.close()
    }

    @Test
    fun onDemandMaterializeLargeUnsigned() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("[9999999999999999999]")
        val arr = doc.getArray()
        val num = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.toULong() shouldBe 9999999999999999999u
        doc.close()
        parser.close()
    }

    @Test
    fun onDemandMaterializeNestedUnsigned() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"big": 18446744073709551615}""")
        val obj = doc.getObject()
        val num = obj["big"].materialize().shouldBeInstanceOf<JsonNumber>()
        num.isUnsigned shouldBe true
        num.toULong() shouldBe ULong.MAX_VALUE
        doc.close()
        parser.close()
    }
}
