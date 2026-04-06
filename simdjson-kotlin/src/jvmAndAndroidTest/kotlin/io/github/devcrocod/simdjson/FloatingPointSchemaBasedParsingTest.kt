package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.schemas.*
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FloatingPointSchemaBasedParsingTest {

    private val parser = SimdJsonParser()

    // Float

    @Test
    fun `float at root`() {
        val result = parser.parse<Float>("3.14")
        result shouldBe 3.14f
    }

    @Test
    fun `float at object field`() {
        val result = parser.parse<DataClassWithPrimitiveFloatField>("""{"field": 1.5}""")
        result.field shouldBe 1.5f
    }

    @Test
    fun `nullable float null at object field`() {
        val result = parser.parse<DataClassWithFloatField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `float array at root`() {
        val result = parser.parse<FloatArray>("[1.0, 2.5, 3.0]")
        result shouldBe floatArrayOf(1.0f, 2.5f, 3.0f)
    }

    @Test
    fun `float array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveFloatArrayField>("""{"field": [1.5, 2.5]}""")
        result.field shouldBe floatArrayOf(1.5f, 2.5f)
    }

    @Test
    fun `float list at object field`() {
        val result = parser.parse<DataClassWithFloatListField>("""{"field": [1.1, 2.2]}""")
        result.field shouldBe listOf(1.1f, 2.2f)
    }

    // Double

    @Test
    fun `double at root`() {
        val result = parser.parse<Double>("3.141592653589793")
        result shouldBe 3.141592653589793
    }

    @Test
    fun `double at object field`() {
        val result = parser.parse<DataClassWithPrimitiveDoubleField>("""{"field": 2.718281828459045}""")
        result.field shouldBe 2.718281828459045
    }

    @Test
    fun `nullable double null at object field`() {
        val result = parser.parse<DataClassWithDoubleField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `double array at root`() {
        val result = parser.parse<DoubleArray>("[1.0, 2.0, 3.0]")
        result shouldBe doubleArrayOf(1.0, 2.0, 3.0)
    }

    @Test
    fun `double array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveDoubleArrayField>("""{"field": [1.5, 2.5]}""")
        result.field shouldBe doubleArrayOf(1.5, 2.5)
    }

    // Validation

    @Test
    fun `integer not accepted as float`() {
        assertThrows<JsonParsingException> {
            parser.parse<Float>("42")
        }
    }

    @Test
    fun `integer not accepted as double`() {
        assertThrows<JsonParsingException> {
            parser.parse<Double>("42")
        }
    }
}
