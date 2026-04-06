package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class DomParsingTest {

    // --- Objects ---

    @Test
    fun `empty object`() {
        val parser = SimdJsonParser()
        val result = parser.parse("{}").shouldBeInstanceOf<JsonObject>()
        result.size shouldBe 0
        result.iterator().hasNext() shouldBe false
    }

    @Test
    fun `object iteration`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"a": 1, "b": 2, "c": 3}""").shouldBeInstanceOf<JsonObject>()

        val expectedKeys = listOf("a", "b", "c")
        val expectedValues = listOf(1L, 2L, 3L)
        var counter = 0
        for ((key, value) in result) {
            key shouldBe expectedKeys[counter]
            val num = value.shouldBeInstanceOf<JsonNumber>()
            num.toLong() shouldBe expectedValues[counter]
            counter++
        }
        counter shouldBe 3
    }

    @Test
    fun `object size`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"1": 1, "2": 1, "3": 1}""").shouldBeInstanceOf<JsonObject>()
        result.size shouldBe 3
    }

    @Test
    fun `object field access`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"name": "alice", "age": 30}""").shouldBeInstanceOf<JsonObject>()

        val name = result["name"].shouldBeInstanceOf<JsonString>()
        name.value shouldBe "alice"

        val age = result["age"].shouldBeInstanceOf<JsonNumber>()
        age.toLong() shouldBe 30L

        result["missing"].shouldBeNull()
    }

    @Test
    fun `object field access with non-ASCII keys`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"ąćśńźż": 1, "\u20A9\u0E3F": 2, "αβγ": 3, "😀abc😀": 4}""")
            .shouldBeInstanceOf<JsonObject>()

        result["ąćśńźż"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        result["\u20A9\u0E3F"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 2L
        result["αβγ"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 3L
        result["😀abc😀"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 4L
    }

    @Test
    fun `object keys`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"x": 1, "y": 2, "z": 3}""").shouldBeInstanceOf<JsonObject>()
        result.keys() shouldBe setOf("x", "y", "z")
    }

    @Test
    fun `object contains`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"a": 1}""").shouldBeInstanceOf<JsonObject>()
        ("a" in result) shouldBe true
        ("b" in result) shouldBe false
    }

    @Test
    fun `object with all value types`() {
        val parser = SimdJsonParser()
        val result = parser.parse(
            """{"s": "hello", "n": 42, "d": 3.14, "t": true, "f": false, "nil": null, "arr": [1], "obj": {"k": "v"}}"""
        ).shouldBeInstanceOf<JsonObject>()
        result.size shouldBe 8

        result["s"].shouldBeInstanceOf<JsonString>().value shouldBe "hello"
        result["n"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L
        result["d"].shouldBeInstanceOf<JsonNumber>().toDouble() shouldBe 3.14
        result["t"].shouldBeInstanceOf<JsonBoolean>().value shouldBe true
        result["f"].shouldBeInstanceOf<JsonBoolean>().value shouldBe false
        result["nil"].shouldBeInstanceOf<JsonNull>()
        result["arr"].shouldBeInstanceOf<JsonArray>().size shouldBe 1
        (result["obj"].shouldBeInstanceOf<JsonObject>()["k"] as JsonString).value shouldBe "v"
    }

    @Test
    fun `single field object`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"only": 1}""").shouldBeInstanceOf<JsonObject>()
        result.size shouldBe 1
        (result["only"] as JsonNumber).toLong() shouldBe 1L
    }

    @Test
    fun `nonexistent field returns null`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"ąćśńźż": 1, "\u20A9\u0E3F": 2, "αβγ": 3}""")
            .shouldBeInstanceOf<JsonObject>()
        result["acsnz"].shouldBeNull()
        result["\\u20A9\\u0E3F"].shouldBeNull()
        result["αβ"].shouldBeNull()
    }

    @Test
    fun `empty nested containers`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"a": {}, "b": [], "c": {"d": {}}}""").shouldBeInstanceOf<JsonObject>()

        val a = result["a"].shouldBeInstanceOf<JsonObject>()
        a.size shouldBe 0

        val b = result["b"].shouldBeInstanceOf<JsonArray>()
        b.size shouldBe 0

        val d = (result["c"] as JsonObject)["d"].shouldBeInstanceOf<JsonObject>()
        d.size shouldBe 0
    }

    // --- Arrays ---

    @Test
    fun `empty array`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[]").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 0
        result.iterator().hasNext() shouldBe false
    }

    @Test
    fun `array iteration`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[1, 2, 3]").shouldBeInstanceOf<JsonArray>()

        val values = result.map { (it as JsonNumber).toLong() }
        values shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `array size`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[1, 2, 3]").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
    }

    @Test
    fun `array index access`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[10, 20, 30]").shouldBeInstanceOf<JsonArray>()

        result[0].shouldBeInstanceOf<JsonNumber>()
        (result[0] as JsonNumber).toLong() shouldBe 10L
        (result[2] as JsonNumber).toLong() shouldBe 30L
    }

    @Test
    fun `array index out of bounds`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[1]").shouldBeInstanceOf<JsonArray>()
        shouldThrow<IndexOutOfBoundsException> { result[1] }
        shouldThrow<IndexOutOfBoundsException> { result[-1] }
    }

    @Test
    fun `single element array`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[99]").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 1
        (result[0] as JsonNumber).toLong() shouldBe 99L
    }

    @Test
    fun `array of strings`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""["a", "b", "c"]""").shouldBeInstanceOf<JsonArray>()
        val values = result.map { (it as JsonString).value }
        values shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `array of booleans`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[true, false, true]").shouldBeInstanceOf<JsonArray>()
        val values = result.map { (it as JsonBoolean).value }
        values shouldBe listOf(true, false, true)
    }

    @Test
    fun `array of nulls`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[null, null, null]").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
        for (element in result) {
            element.shouldBeInstanceOf<JsonNull>()
        }
    }

    @Test
    fun `nested arrays`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[[1, 2], [3, 4], [5]]").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3

        val first = result[0].shouldBeInstanceOf<JsonArray>()
        first.size shouldBe 2
        (first[0] as JsonNumber).toLong() shouldBe 1L
        (first[1] as JsonNumber).toLong() shouldBe 2L

        val third = result[2].shouldBeInstanceOf<JsonArray>()
        third.size shouldBe 1
    }

    @Test
    fun `array iterator exhaustion`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[1, 2]").shouldBeInstanceOf<JsonArray>()
        val it = result.iterator()
        it.next()
        it.next()
        it.hasNext() shouldBe false
        shouldThrow<NoSuchElementException> { it.next() }
    }

    @Test
    fun `object iterator exhaustion`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"a": 1}""").shouldBeInstanceOf<JsonObject>()
        val it = result.iterator()
        it.next()
        it.hasNext() shouldBe false
        shouldThrow<NoSuchElementException> { it.next() }
    }

    @Test
    fun `array of objects`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""[{"a": 1}, {"a": 2}, {"a": 3}]""").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
        for ((i, element) in result.withIndex()) {
            val obj = element.shouldBeInstanceOf<JsonObject>()
            (obj["a"] as JsonNumber).toLong() shouldBe (i + 1).toLong()
        }
    }

    // --- Primitives at root ---

    @Test
    fun `string at root`() {
        val parser = SimdJsonParser()
        val result = parser.parse(""""hello"""").shouldBeInstanceOf<JsonString>()
        result.value shouldBe "hello"
    }

    @Test
    fun `integer at root`() {
        val parser = SimdJsonParser()
        val result = parser.parse("42").shouldBeInstanceOf<JsonNumber>()
        result.isInteger shouldBe true
        result.isLong shouldBe true
        result.toLong() shouldBe 42L
        result.toInt() shouldBe 42
    }

    @Test
    fun `negative integer at root`() {
        val parser = SimdJsonParser()
        val result = parser.parse("-123").shouldBeInstanceOf<JsonNumber>()
        result.toLong() shouldBe -123L
    }

    @Test
    fun `double at root`() {
        val parser = SimdJsonParser()
        val result = parser.parse("3.14").shouldBeInstanceOf<JsonNumber>()
        result.isInteger shouldBe false
        result.toDouble() shouldBe 3.14
    }

    @Test
    fun `true at root`() {
        val parser = SimdJsonParser()
        val result = parser.parse("true").shouldBeInstanceOf<JsonBoolean>()
        result.value shouldBe true
    }

    @Test
    fun `false at root`() {
        val parser = SimdJsonParser()
        val result = parser.parse("false").shouldBeInstanceOf<JsonBoolean>()
        result.value shouldBe false
    }

    @Test
    fun `null at root`() {
        val parser = SimdJsonParser()
        parser.parse("null").shouldBeInstanceOf<JsonNull>()
    }

    @Test
    fun `string with escapes`() {
        val parser = SimdJsonParser()
        val result = parser.parse("\"hello\\nworld\\t!\\\\\\\"\"").shouldBeInstanceOf<JsonString>()
        result.value shouldBe "hello\nworld\t!\\\""
    }

    @Test
    fun `string with unicode`() {
        val parser = SimdJsonParser()
        val result = parser.parse("\"\\u0048\\u0065\\u006C\\u006C\\u006F\"").shouldBeInstanceOf<JsonString>()
        result.value shouldBe "Hello"
    }

    @Test
    fun `empty string`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""""""").shouldBeInstanceOf<JsonString>()
        result.value shouldBe ""
    }

    @Test
    fun `Long MAX_VALUE`() {
        val parser = SimdJsonParser()
        val result = parser.parse("${Long.MAX_VALUE}").shouldBeInstanceOf<JsonNumber>()
        result.isLong shouldBe true
        result.toLong() shouldBe Long.MAX_VALUE
    }

    @Test
    fun `Long MIN_VALUE`() {
        val parser = SimdJsonParser()
        val result = parser.parse("${Long.MIN_VALUE}").shouldBeInstanceOf<JsonNumber>()
        result.isLong shouldBe true
        result.toLong() shouldBe Long.MIN_VALUE
    }

    @Test
    fun `negative double`() {
        val parser = SimdJsonParser()
        val result = parser.parse("-1.5").shouldBeInstanceOf<JsonNumber>()
        result.isInteger shouldBe false
        result.toDouble() shouldBe -1.5
    }

    @Test
    fun `scientific notation`() {
        val parser = SimdJsonParser()
        val result = parser.parse("1.5e10").shouldBeInstanceOf<JsonNumber>()
        result.toDouble() shouldBe 1.5e10
    }

    @Test
    fun `zero integer`() {
        val parser = SimdJsonParser()
        val result = parser.parse("0").shouldBeInstanceOf<JsonNumber>()
        result.isInteger shouldBe true
        result.toLong() shouldBe 0L
    }

    // --- Nested structures ---

    @Test
    fun `nested object in array`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""[{"a": 1}, {"b": 2}]""").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 2

        val first = result[0].shouldBeInstanceOf<JsonObject>()
        (first["a"] as JsonNumber).toLong() shouldBe 1L

        val second = result[1].shouldBeInstanceOf<JsonObject>()
        (second["b"] as JsonNumber).toLong() shouldBe 2L
    }

    @Test
    fun `nested array in object`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"nums": [1, 2, 3]}""").shouldBeInstanceOf<JsonObject>()

        val nums = result["nums"].shouldBeInstanceOf<JsonArray>()
        nums.size shouldBe 3
        (nums[1] as JsonNumber).toLong() shouldBe 2L
    }

    @Test
    fun `deeply nested`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"a": {"b": {"c": 42}}}""").shouldBeInstanceOf<JsonObject>()

        val a = result["a"].shouldBeInstanceOf<JsonObject>()
        val b = a["b"].shouldBeInstanceOf<JsonObject>()
        val c = b["c"].shouldBeInstanceOf<JsonNumber>()
        c.toLong() shouldBe 42L
    }

    @Test
    fun `mixed type array`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""[1, "two", true, null, 3.14, {"k": "v"}, []]""")
            .shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 7

        result[0].shouldBeInstanceOf<JsonNumber>()
        result[1].shouldBeInstanceOf<JsonString>()
        result[2].shouldBeInstanceOf<JsonBoolean>()
        result[3].shouldBeInstanceOf<JsonNull>()
        result[4].shouldBeInstanceOf<JsonNumber>()
        result[5].shouldBeInstanceOf<JsonObject>()
        result[6].shouldBeInstanceOf<JsonArray>()
    }

    @Test
    fun `deeply nested arrays`() {
        val parser = SimdJsonParser()
        val result = parser.parse("[[[[42]]]]").shouldBeInstanceOf<JsonArray>()
        val l1 = result[0].shouldBeInstanceOf<JsonArray>()
        val l2 = l1[0].shouldBeInstanceOf<JsonArray>()
        val l3 = l2[0].shouldBeInstanceOf<JsonArray>()
        (l3[0] as JsonNumber).toLong() shouldBe 42L
    }

    @Test
    fun `complex mixed nesting`() {
        val parser = SimdJsonParser()
        val json = """{"users": [{"name": "alice", "tags": ["admin", "user"]}, {"name": "bob", "tags": []}]}"""
        val result = parser.parse(json).shouldBeInstanceOf<JsonObject>()

        val users = result["users"].shouldBeInstanceOf<JsonArray>()
        users.size shouldBe 2

        val alice = users[0].shouldBeInstanceOf<JsonObject>()
        (alice["name"] as JsonString).value shouldBe "alice"
        val aliceTags = alice["tags"].shouldBeInstanceOf<JsonArray>()
        aliceTags.size shouldBe 2
        (aliceTags[0] as JsonString).value shouldBe "admin"

        val bob = users[1].shouldBeInstanceOf<JsonObject>()
        val bobTags = bob["tags"].shouldBeInstanceOf<JsonArray>()
        bobTags.size shouldBe 0
    }

    @Test
    fun `deep nesting 10 levels`() {
        val parser = SimdJsonParser()
        val depth = 10
        val open = "{\"a\":".repeat(depth)
        val close = "}".repeat(depth)
        val json = "${open}42${close}"
        val result = parser.parse(json)

        var current: JsonValue = result
        repeat(depth) {
            current = current.shouldBeInstanceOf<JsonObject>()["a"]!!
        }
        current.shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L
    }

    @Test
    fun `max_depth exceeded throws exception`() {
        val parser = SimdJsonParser(maxDepth = 2)
        val json = """{"a":{"b":{"c":1}}}"""  // 3 levels deep
        shouldThrow<JsonParsingException> {
            parser.parse(json)
        }
    }

    @Test
    fun `parsing within max_depth succeeds`() {
        val parser = SimdJsonParser(maxDepth = 3)
        val json = """{"a":{"b":1}}"""  // 2 levels deep, within limit of 3
        val result = parser.parse(json).shouldBeInstanceOf<JsonObject>()
        val a = result["a"].shouldBeInstanceOf<JsonObject>()
        a["b"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
    }

    @Test
    fun `array in array in object`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"matrix": [[1, 2], [3, 4]]}""").shouldBeInstanceOf<JsonObject>()

        val matrix = result["matrix"].shouldBeInstanceOf<JsonArray>()
        matrix.size shouldBe 2

        val row0 = matrix[0].shouldBeInstanceOf<JsonArray>()
        (row0[0] as JsonNumber).toLong() shouldBe 1L
        (row0[1] as JsonNumber).toLong() shouldBe 2L

        val row1 = matrix[1].shouldBeInstanceOf<JsonArray>()
        (row1[0] as JsonNumber).toLong() shouldBe 3L
        (row1[1] as JsonNumber).toLong() shouldBe 4L
    }

    // --- Error cases ---

    @Test
    fun `unclosed object`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("""{"a": 1""") }
    }

    @Test
    fun `unclosed array`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("[1, 2") }
    }

    @Test
    fun `missing comma in object`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("""{"a": 1 "b": 2}""") }
    }

    @Test
    fun `missing comma in array`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("[1 2]") }
    }

    @Test
    fun `trailing content`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("[1] [2]") }
    }

    @Test
    fun `empty input`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("") }
    }

    @Test
    fun `too many commas in array`() {
        val parser = SimdJsonParser()
        shouldThrow<JsonParsingException> { parser.parse("[1,,2]") }
        shouldThrow<JsonParsingException> { parser.parse("[,]") }
    }

    @Test
    fun `unclosed object due to passed length`() {
        val parser = SimdJsonParser()
        val bytes = """{"a":{}}""".encodeToByteArray()
        shouldThrow<JsonParsingException> { parser.parse(bytes, bytes.size - 1) }
    }

    // --- Whitespace handling ---

    @Test
    fun `whitespace around values`() {
        val parser = SimdJsonParser()
        val result = parser.parse("""  {  "a"  :  1  ,  "b"  :  2  }  """).shouldBeInstanceOf<JsonObject>()
        result.size shouldBe 2
        (result["a"] as JsonNumber).toLong() shouldBe 1L
    }

    @Test
    fun `whitespace in array`() {
        val parser = SimdJsonParser()
        val result = parser.parse("  [  1  ,  2  ,  3  ]  ").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
    }

    // --- Parser reuse ---

    @Test
    fun `parser reuse`() {
        val parser = SimdJsonParser()

        val r1 = parser.parse("[1, 2]").shouldBeInstanceOf<JsonArray>()
        r1.size shouldBe 2

        val r2 = parser.parse("""{"x": 99}""").shouldBeInstanceOf<JsonObject>()
        (r2["x"] as JsonNumber).toLong() shouldBe 99L
    }

    // --- ByteArray API ---

    @Test
    fun `parse byte array`() {
        val parser = SimdJsonParser()
        val bytes = """{"key": "value"}""".encodeToByteArray()
        val result = parser.parse(bytes, bytes.size).shouldBeInstanceOf<JsonObject>()
        (result["key"] as JsonString).value shouldBe "value"
    }
}
