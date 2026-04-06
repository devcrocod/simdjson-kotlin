package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.schemas.DataClassWithPrimitiveIntArrayField
import io.github.devcrocod.simdjson.schemas.DataClassWithPrimitiveLongArrayField
import io.github.devcrocod.simdjson.schemas.DataClassWithStringArrayField
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArraySchemaBasedParsingTest {

    private val parser = SimdJsonParser()

    // Root arrays

    @Test
    fun `empty int array at root`() {
        val result = parser.parse<IntArray>("[]")
        result shouldBe intArrayOf()
    }

    @Test
    fun `int array at root`() {
        val result = parser.parse<IntArray>("[1, 2, 3]")
        result shouldBe intArrayOf(1, 2, 3)
    }

    @Test
    fun `long array at root`() {
        val result = parser.parse<LongArray>("[100, 200, 300]")
        result shouldBe longArrayOf(100, 200, 300)
    }

    @Test
    fun `double array at root`() {
        val result = parser.parse<DoubleArray>("[1.0, 2.0, 3.0]")
        result shouldBe doubleArrayOf(1.0, 2.0, 3.0)
    }

    @Test
    fun `string array at root`() {
        val result = parser.parse<Array<String?>>("""["a", "b", "c"]""")
        result shouldBe arrayOf("a", "b", "c")
    }

    @Test
    fun `null array at root`() {
        val result = parser.parse<IntArray>("null")
        result.shouldBeNull()
    }

    @Test
    fun `string array with nulls at root`() {
        val result = parser.parse<Array<String?>>("""["a", null, "c"]""")
        result.size shouldBe 3
        result[0] shouldBe "a"
        result[1].shouldBeNull()
        result[2] shouldBe "c"
    }

    // Nested arrays (2D)

    @Test
    fun `2D int array at root`() {
        val result = parser.parse<Array<IntArray?>>("[[1, 2], [3, 4]]")
        result.shouldNotBeNull()
        result.size shouldBe 2
        result[0] shouldBe intArrayOf(1, 2)
        result[1] shouldBe intArrayOf(3, 4)
    }

    @Test
    fun `2D string array at root`() {
        val result = parser.parse<Array<Array<String?>?>>("""[["a", "b"], ["c"]]""")
        result.size shouldBe 2
        result[0] shouldBe arrayOf("a", "b")
        result[1] shouldBe arrayOf("c")
    }

    // Arrays in objects

    @Test
    fun `int array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveIntArrayField>("""{"field": [10, 20]}""")
        result.field shouldBe intArrayOf(10, 20)
    }

    @Test
    fun `null array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveIntArrayField>("""{"field": null}""")
        result.field.shouldBeNull()
    }

    @Test
    fun `long array at object field`() {
        val result = parser.parse<DataClassWithPrimitiveLongArrayField>("""{"field": [100, 200]}""")
        result.field shouldBe longArrayOf(100, 200)
    }

    @Test
    fun `string array at object field`() {
        val result = parser.parse<DataClassWithStringArrayField>("""{"field": ["x", "y"]}""")
        result.field shouldBe arrayOf("x", "y")
    }

    // Validation

    @Test
    fun `list at root is not supported`() {
        assertThrows<JsonParsingException> {
            parser.parse("[1, 2]", List::class)
        }
    }

    @Test
    fun `unclosed array`() {
        assertThrows<JsonParsingException> {
            parser.parse<IntArray>("[1, 2")
        }
    }

    @Test
    fun `type mismatch - object instead of array`() {
        assertThrows<JsonParsingException> {
            parser.parse<IntArray>("""{"key": 1}""")
        }
    }

    @Test
    fun `unsupported container type - Set`() {
        assertThrows<JsonParsingException> {
            parser.parse("[1, 2]", Set::class)
        }
    }
}
