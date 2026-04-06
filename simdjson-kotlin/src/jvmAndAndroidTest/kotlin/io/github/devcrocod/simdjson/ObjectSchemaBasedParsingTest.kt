package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.schemas.*
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ObjectSchemaBasedParsingTest {

    private val parser = SimdJsonParser()

    @Test
    fun `empty object with nullable field`() {
        val result = parser.parse<DataClassWithIntField>("{}")
        result.shouldNotBeNull()
        result.field.shouldBeNull()
    }

    @Test
    fun `empty object with primitive field throws`() {
        assertThrows<JsonParsingException> {
            parser.parse<DataClassWithPrimitiveIntField>("{}")
        }
    }

    @Test
    fun `null at root when object is expected`() {
        val result = parser.parse("null", DataClassWithIntField::class)
        result.shouldBeNull()
    }

    @Test
    fun `data class with single field`() {
        val result = parser.parse<DataClassWithStringField>("""{"field": "hello"}""")
        result.field shouldBe "hello"
    }

    @Test
    fun `data class with multiple fields`() {
        val result = parser.parse<DataClassWithMultipleFields>("""{"name": "Alice", "age": 30, "active": true}""")
        result.name shouldBe "Alice"
        result.age shouldBe 30
        result.active shouldBe true
    }

    @Test
    fun `data class with missing fields gets nulls`() {
        val result = parser.parse<DataClassWithMultipleFields>("""{"name": "Bob"}""")
        result.name shouldBe "Bob"
        result.age.shouldBeNull()
        result.active.shouldBeNull()
    }

    @Test
    fun `extra JSON fields are silently skipped`() {
        val result = parser.parse<DataClassWithStringField>("""{"extra": 1, "field": "ok", "more": true}""")
        result.field shouldBe "ok"
    }

    @Test
    fun `nested data class`() {
        val result = parser.parse<NestedDataClass>("""{"nestedField": {"field": "inner"}}""")
        result.nestedField.shouldNotBeNull()
        result.nestedField.field shouldBe "inner"
    }

    @Test
    fun `null nested object`() {
        val result = parser.parse<NestedDataClass>("""{"nestedField": null}""")
        result.nestedField.shouldBeNull()
    }

    @Test
    fun `annotated class with JsonFieldName`() {
        val result = parser.parse<AnnotatedClassWithStringField>("""{"field": "test"}""")
        result.field shouldBe "test"
    }

    @Test
    fun `annotated class with int field`() {
        val result = parser.parse<AnnotatedClassWithIntField>("""{"field": 42}""")
        result.field shouldBe 42
    }

    @Test
    fun `annotated class with primitive int field`() {
        val result = parser.parse<AnnotatedClassWithPrimitiveIntField>("""{"field": 99}""")
        result.field shouldBe 99
    }

    @Test
    fun `explicit field name mapping`() {
        val result = parser.parse<DataClassWithExplicitFieldNames>("""{"json_name": "test", "json_age": 25}""")
        result.kotlinName shouldBe "test"
        result.kotlinAge shouldBe 25
    }

    @Test
    fun `unicode field name`() {
        val result = parser.parse<DataClassWithUnicodeFieldName>("""{"ąćśńźż": "value"}""")
        result.field shouldBe "value"
    }

    @Test
    fun `array of objects at root`() {
        val result = parser.parse<Array<DataClassWithIntField?>>("""[{"field": 1}, {"field": 2}]""")
        result.size shouldBe 2
        result[0]!!.field shouldBe 1
        result[1]!!.field shouldBe 2
    }

    @Test
    fun `array of objects at object field`() {
        val result = parser.parse<DataClassWithObjectArrayField>("""{"field": [{"field": 10}, {"field": 20}]}""")
        result.field.shouldNotBeNull()
        result.field.size shouldBe 2
        result.field[0]!!.field shouldBe 10
    }

    @Test
    fun `list of objects at object field`() {
        val result = parser.parse<DataClassWithObjectListField>("""{"field": [{"field": 1}, {"field": 2}]}""")
        result.field.shouldNotBeNull()
        result.field.size shouldBe 2
        result.field[0].field shouldBe 1
    }

    @Test
    fun `2D int array at object field`() {
        val result = parser.parse<DataClassWith2DIntArray>("""{"field": [[1, 2], [3, 4]]}""")
        result.field.shouldNotBeNull()
        result.field.size shouldBe 2
        result.field[0] shouldBe intArrayOf(1, 2)
        result.field[1] shouldBe intArrayOf(3, 4)
    }

    @Test
    fun `interface is not supported`() {
        assertThrows<JsonParsingException> {
            parser.parse<Runnable>("{}")
        }
    }

    @Test
    fun `abstract class is not supported`() {
        assertThrows<JsonParsingException> {
            parser.parse<Number>("1")
        }
    }

    @Test
    fun `list at root is not supported`() {
        assertThrows<JsonParsingException> {
            parser.parse("[1, 2, 3]", List::class)
        }
    }

    @Test
    fun `class without JsonFieldName annotation throws`() {
        assertThrows<JsonParsingException> {
            parser.parse<ClassWithoutAnnotation>("""{"field": 1}""")
        }
    }

    @Test
    fun `unclosed object`() {
        assertThrows<JsonParsingException> {
            parser.parse<DataClassWithIntField>("""{"field": 1""")
        }
    }

    @Test
    fun `type mismatch - string instead of object`() {
        assertThrows<JsonParsingException> {
            parser.parse<DataClassWithIntField>(""""not an object"""")
        }
    }
}

// Test helper: non-data class without @JsonFieldName
class ClassWithoutAnnotation(val field: Int)
