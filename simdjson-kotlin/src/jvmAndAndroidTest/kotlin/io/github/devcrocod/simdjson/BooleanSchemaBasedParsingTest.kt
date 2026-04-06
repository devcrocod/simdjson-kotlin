package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.schemas.*
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BooleanSchemaBasedParsingTest {

    private val parser = SimdJsonParser()

    @Test
    fun `true at root`() {
        val result = parser.parse<Boolean>("true")
        result shouldBe true
    }

    @Test
    fun `false at root`() {
        val result = parser.parse<Boolean>("false")
        result shouldBe false
    }

    @Test
    fun `null at root when Boolean is expected`() {
        val result = parser.parse("null".encodeToByteArray(), "null".length, java.lang.Boolean::class.java) as Boolean?
        result.shouldBeNull()
    }

    @Test
    fun `null at root when primitive boolean is expected`() {
        assertThrows<JsonParsingException> {
            parser.parse("null", Boolean::class)
        }
    }

    @Test
    fun `boolean at object field`() {
        val result = parser.parse<DataClassWithBooleanField>("""{"field": true}""")
        result.field shouldBe true
    }

    @Test
    fun `boolean false at object field`() {
        val result = parser.parse<DataClassWithBooleanField>("""{"field": false}""")
        result.field shouldBe false
    }

    @Test
    fun `primitive boolean at object field`() {
        val result = parser.parse<DataClassWithPrimitiveBooleanField>("""{"field": true}""")
        result.field shouldBe true
    }

    @Test
    fun `null boolean at object field`() {
        val result = parser.parse<DataClassWithBooleanField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `boolean array at root`() {
        val result = parser.parse<BooleanArray>("[true, false, true]")
        result shouldBe booleanArrayOf(true, false, true)
    }

    @Test
    fun `empty boolean array at root`() {
        val result = parser.parse<BooleanArray>("[]")
        result shouldBe booleanArrayOf()
    }

    @Test
    fun `boolean array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveBooleanArrayField>("""{"field": [true, false]}""")
        result.field shouldBe booleanArrayOf(true, false)
    }

    @Test
    fun `boxed boolean array at object field`() {
        val result = parser.parse<DataClassWithBooleanArrayField>("""{"field": [true, null, false]}""")
        result.field shouldBe arrayOf(true, null, false)
    }

    @Test
    fun `boolean list at object field`() {
        val result = parser.parse<DataClassWithBooleanListField>("""{"field": [true, false, true]}""")
        result.field shouldBe listOf(true, false, true)
    }

    @Test
    fun `null boolean list at object field`() {
        val result = parser.parse<DataClassWithBooleanListField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `invalid boolean value`() {
        assertThrows<JsonParsingException> {
            parser.parse<Boolean>("truee")
        }
    }

    @Test
    fun `number instead of boolean`() {
        assertThrows<JsonParsingException> {
            parser.parse<Boolean>("1")
        }
    }
}
