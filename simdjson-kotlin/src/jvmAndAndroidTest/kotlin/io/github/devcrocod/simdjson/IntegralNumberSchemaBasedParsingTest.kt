package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.schemas.*
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IntegralNumberSchemaBasedParsingTest {

    private val parser = SimdJsonParser()

    // Byte

    @Test
    fun `byte at root`() {
        val result = parser.parse<Byte>("42")
        result shouldBe 42.toByte()
    }

    @Test
    fun `byte min value`() {
        val result = parser.parse<Byte>("${Byte.MIN_VALUE}")
        result shouldBe Byte.MIN_VALUE
    }

    @Test
    fun `byte max value`() {
        val result = parser.parse<Byte>("${Byte.MAX_VALUE}")
        result shouldBe Byte.MAX_VALUE
    }

    @Test
    fun `byte out of range`() {
        assertThrows<JsonParsingException> {
            parser.parse<Byte>("128")
        }
    }

    @Test
    fun `byte at object field`() {
        val result = parser.parse<DataClassWithPrimitiveByteField>("""{"field": 10}""")
        result.field shouldBe 10.toByte()
    }

    @Test
    fun `nullable byte null at object field`() {
        val result = parser.parse<DataClassWithByteField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    // Short

    @Test
    fun `short at root`() {
        val result = parser.parse<Short>("1000")
        result shouldBe 1000.toShort()
    }

    @Test
    fun `short min value`() {
        val result = parser.parse<Short>("${Short.MIN_VALUE}")
        result shouldBe Short.MIN_VALUE
    }

    @Test
    fun `short max value`() {
        val result = parser.parse<Short>("${Short.MAX_VALUE}")
        result shouldBe Short.MAX_VALUE
    }

    @Test
    fun `short out of range`() {
        assertThrows<JsonParsingException> {
            parser.parse<Short>("32768")
        }
    }

    @Test
    fun `short at object field`() {
        val result = parser.parse<DataClassWithPrimitiveShortField>("""{"field": 500}""")
        result.field shouldBe 500.toShort()
    }

    @Test
    fun `nullable short null at object field`() {
        val result = parser.parse<DataClassWithShortField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    // Int

    @Test
    fun `int at root`() {
        val result = parser.parse<Int>("42")
        result shouldBe 42
    }

    @Test
    fun `int min value`() {
        val result = parser.parse<Int>("${Int.MIN_VALUE}")
        result shouldBe Int.MIN_VALUE
    }

    @Test
    fun `int max value`() {
        val result = parser.parse<Int>("${Int.MAX_VALUE}")
        result shouldBe Int.MAX_VALUE
    }

    @Test
    fun `int out of range`() {
        assertThrows<JsonParsingException> {
            parser.parse<Int>("2147483648")
        }
    }

    @Test
    fun `int at object field`() {
        val result = parser.parse<DataClassWithPrimitiveIntField>("""{"field": 123}""")
        result.field shouldBe 123
    }

    @Test
    fun `nullable int null at object field`() {
        val result = parser.parse<DataClassWithIntField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `int array at root`() {
        val result = parser.parse<IntArray>("[1, 2, 3]")
        result shouldBe intArrayOf(1, 2, 3)
    }

    @Test
    fun `empty int array at root`() {
        val result = parser.parse<IntArray>("[]")
        result shouldBe intArrayOf()
    }

    @Test
    fun `int array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveIntArrayField>("""{"field": [10, 20, 30]}""")
        result.field shouldBe intArrayOf(10, 20, 30)
    }

    @Test
    fun `int list at object field`() {
        val result = parser.parse<DataClassWithIntListField>("""{"field": [1, 2, 3]}""")
        result.field shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `null int list at object field`() {
        val result = parser.parse<DataClassWithIntListField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    // Long

    @Test
    fun `long at root`() {
        val result = parser.parse<Long>("9223372036854775807")
        result shouldBe Long.MAX_VALUE
    }

    @Test
    fun `long min value`() {
        val result = parser.parse<Long>("${Long.MIN_VALUE}")
        result shouldBe Long.MIN_VALUE
    }

    @Test
    fun `long at object field`() {
        val result = parser.parse<DataClassWithPrimitiveLongField>("""{"field": 100}""")
        result.field shouldBe 100L
    }

    @Test
    fun `nullable long null at object field`() {
        val result = parser.parse<DataClassWithLongField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    // Validation

    @Test
    fun `leading zeroes rejected`() {
        assertThrows<JsonParsingException> {
            parser.parse<Int>("01")
        }
    }

    @Test
    fun `minus without digit rejected`() {
        assertThrows<JsonParsingException> {
            parser.parse<Int>("-")
        }
    }
}
