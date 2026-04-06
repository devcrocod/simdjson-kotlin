package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.schemas.*
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StringSchemaBasedParsingTest {

    private val parser = SimdJsonParser()

    @Test
    fun `string at root`() {
        val result = parser.parse<String>(""""hello"""")
        result shouldBe "hello"
    }

    @Test
    fun `empty string at root`() {
        val result = parser.parse<String>("""""""")
        result shouldBe ""
    }

    @Test
    fun `null string at root`() {
        val result = parser.parse<String>("null")
        result.shouldBeNull()
    }

    @Test
    fun `string at object field`() {
        val result = parser.parse<DataClassWithStringField>("""{"field": "value"}""")
        result.field shouldBe "value"
    }

    @Test
    fun `null string at object field`() {
        val result = parser.parse<DataClassWithStringField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `string with escapes`() {
        val result = parser.parse<String>(""""hello\\nworld"""")
        result shouldBe "hello\\nworld"
    }

    @Test
    fun `string with unicode escape`() {
        // JSON: "\u0041" — raw string so \ is literal
        val result = parser.parse<String>(""""\u0041"""")
        result shouldBe "A"
    }

    @Test
    fun `string array at root`() {
        val result = parser.parse<Array<String?>>("""["a", "b", "c"]""")
        result shouldBe arrayOf("a", "b", "c")
    }

    @Test
    fun `empty string array at root`() {
        val result = parser.parse<Array<String?>>("[]")
        result shouldBe arrayOf<String>()
    }

    @Test
    fun `string array at object field`() {
        val result = parser.parse<DataClassWithStringArrayField>("""{"field": ["x", "y"]}""")
        result.field shouldBe arrayOf("x", "y")
    }

    @Test
    fun `string list at object field`() {
        val result = parser.parse<DataClassWithStringListField>("""{"field": ["a", "b", "c"]}""")
        result.field shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `null string list at object field`() {
        val result = parser.parse<DataClassWithStringListField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    // Char

    @Test
    fun `char at root`() {
        val result = parser.parse<Char>(""""A"""")
        result shouldBe 'A'
    }

    @Test
    fun `char at object field`() {
        val result = parser.parse<DataClassWithPrimitiveCharField>("""{"field": "X"}""")
        result.field shouldBe 'X'
    }

    @Test
    fun `nullable char null at object field`() {
        val result = parser.parse<DataClassWithCharField>("""{"field": null}""")
        result.field.shouldBeNull()
    }
}
