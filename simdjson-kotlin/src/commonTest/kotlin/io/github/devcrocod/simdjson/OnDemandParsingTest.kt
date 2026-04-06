package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class OnDemandParsingTest {

    // --- Root primitives ---

    @Test
    fun `root long`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("42")
        doc.getLong() shouldBe 42L
    }

    @Test
    fun `root negative long`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("-123")
        doc.getLong() shouldBe -123L
    }

    @Test
    fun `root double`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("3.14")
        doc.getDouble() shouldBe 3.14
    }

    @Test
    fun `root boolean`() {
        val parser = SimdJsonParser()
        parser.iterate("true").getBoolean() shouldBe true
        parser.iterate("false").getBoolean() shouldBe false
    }

    @Test
    fun `root string`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate(""""hello"""")
        doc.getString() shouldBe "hello"
    }

    @Test
    fun `root null`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("null")
        doc.isNull() shouldBe true
    }

    @Test
    fun `root not null`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("42")
        doc.isNull() shouldBe false
        doc.getLong() shouldBe 42L
    }

    @Test
    fun `root ulong`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("18446744073709551615")
        doc.getULong() shouldBe ULong.MAX_VALUE
    }

    // --- Root type detection ---

    @Test
    fun `root get type`() {
        val parser = SimdJsonParser()
        parser.iterate("{}").getType() shouldBe JsonType.OBJECT
        parser.iterate("[]").getType() shouldBe JsonType.ARRAY
        parser.iterate(""""hi"""").getType() shouldBe JsonType.STRING
        parser.iterate("42").getType() shouldBe JsonType.NUMBER
        parser.iterate("true").getType() shouldBe JsonType.BOOLEAN
        parser.iterate("null").getType() shouldBe JsonType.NULL
    }

    // --- Empty containers ---

    @Test
    fun `empty object`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("{}").getObject()
        obj.iterator().hasNext() shouldBe false
    }

    @Test
    fun `empty array`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[]").getArray()
        arr.iterator().hasNext() shouldBe false
    }

    // --- Array iteration ---

    @Test
    fun `array of ints`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[1, 2, 3]").getArray()
        val values = arr.map { it.getLong() }
        values shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `array of strings`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""["a", "b", "c"]""").getArray()
        val values = arr.map { it.getString() }
        values shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `array of mixed types`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[1, "two", true, null, 3.14]""").getArray()
        val iter = arr.iterator()

        iter.next().getLong() shouldBe 1L
        iter.next().getString() shouldBe "two"
        iter.next().getBoolean() shouldBe true
        iter.next().isNull() shouldBe true
        iter.next().getDouble() shouldBe 3.14
        iter.hasNext() shouldBe false
    }

    @Test
    fun `single element array`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val values = arr.map { it.getLong() }
        values shouldBe listOf(42L)
    }

    // --- Object iteration ---

    @Test
    fun `object iteration`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        val entries = mutableListOf<Pair<String, Long>>()
        for (field in obj) {
            entries.add(field.name to field.value.getLong())
        }
        entries shouldBe listOf("a" to 1L, "b" to 2L, "c" to 3L)
    }

    @Test
    fun `object find field`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        obj.findField("a").getLong() shouldBe 1L
        obj.findField("b").getLong() shouldBe 2L
        obj.findField("c").getLong() shouldBe 3L
    }

    @Test
    fun `object get operator`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        // Unordered access -- can access in any order
        obj["b"].getLong() shouldBe 2L
        obj["a"].getLong() shouldBe 1L
        obj["c"].getLong() shouldBe 3L
    }

    @Test
    fun `object get - not found throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1}""").getObject()
        shouldThrow<JsonParsingException> { obj["z"] }
    }

    // --- Nested structures ---

    @Test
    fun `nested object in object`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"outer": {"inner": 42}}""")
        val inner = doc.getObject().findField("outer").getObject()
        inner.findField("inner").getLong() shouldBe 42L
    }

    @Test
    fun `nested array in object`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"arr": [1, 2, 3]}""")
        val arr = doc.getObject().findField("arr").getArray()
        arr.map { it.getLong() } shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `nested object in array`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"a": 1}, {"a": 2}]""").getArray()
        val values = arr.map { it.getObject().findField("a").getLong() }
        values shouldBe listOf(1L, 2L)
    }

    @Test
    fun `deeply nested`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"a": {"b": {"c": [1, 2, 3]}}}""")
        val arr = doc.getObject()
            .findField("a").getObject()
            .findField("b").getObject()
            .findField("c").getArray()
        arr.map { it.getLong() } shouldBe listOf(1L, 2L, 3L)
    }

    // --- Auto-skip of unconsumed values ---

    @Test
    fun `auto-skip array elements`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[[1, 2], [3, 4], [5, 6]]""").getArray()
        val iter = arr.iterator()
        // Skip first element without consuming it
        iter.next() // [1, 2] -- not consumed
        iter.next() // [3, 4] -- not consumed
        val last = iter.next().getArray()
        last.map { it.getLong() } shouldBe listOf(5L, 6L)
    }

    @Test
    fun `auto-skip object values`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": [1, 2], "b": [3, 4], "c": 5}""").getObject()
        val iter = obj.iterator()
        iter.next() // skip "a"
        iter.next() // skip "b"
        val field = iter.next()
        field.name shouldBe "c"
        field.value.getLong() shouldBe 5L
    }

    // --- Type detection on values ---

    @Test
    fun `value get type`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[1, "hello", true, null, {}, []]""").getArray()
        val types = arr.map { v ->
            val t = v.getType()
            // Consume the value so iteration can continue
            when (t) {
                JsonType.NUMBER -> v.getLong()
                JsonType.STRING -> v.getString()
                JsonType.BOOLEAN -> v.getBoolean()
                JsonType.NULL -> v.isNull()
                JsonType.OBJECT -> v.getObject()
                JsonType.ARRAY -> v.getArray()
            }
            t
        }
        types shouldBe listOf(
            JsonType.NUMBER, JsonType.STRING, JsonType.BOOLEAN,
            JsonType.NULL, JsonType.OBJECT, JsonType.ARRAY
        )
    }

    // --- Consumption tracking ---

    @Test
    fun `double consume document throws`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("42")
        doc.getLong()
        shouldThrow<JsonIterationException> { doc.getLong() }
    }

    @Test
    fun `double consume value throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[1]").getArray()
        val value = arr.iterator().next()
        value.getLong()
        shouldThrow<JsonIterationException> { value.getLong() }
    }

    @Test
    fun `double iterate array throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[1, 2]").getArray()
        arr.iterator() // first call ok
        shouldThrow<JsonIterationException> { arr.iterator() }
    }

    @Test
    fun `double iterate object throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1}""").getObject()
        obj.iterator() // first call ok
        shouldThrow<JsonIterationException> { obj.iterator() }
    }

    // --- materialize() ---

    @Test
    fun `materialize long`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        value.toLong() shouldBe 42L
    }

    @Test
    fun `materialize double`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[3.14]").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        value.toDouble() shouldBe 3.14
    }

    @Test
    fun `materialize string`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""["hello"]""").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonString>()
        value.value shouldBe "hello"
    }

    @Test
    fun `materialize boolean`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[true]").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonBoolean>()
        value.value shouldBe true
    }

    @Test
    fun `materialize null`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[null]").getArray()
        arr.iterator().next().materialize().shouldBeInstanceOf<JsonNull>()
    }

    @Test
    fun `materialize object`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"a": 1, "b": "two"}]""").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        value.size shouldBe 2
        val a = value["a"].shouldBeInstanceOf<JsonNumber>()
        a.toLong() shouldBe 1L
        val b = value["b"].shouldBeInstanceOf<JsonString>()
        b.value shouldBe "two"
    }

    @Test
    fun `materialize array`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[1, 2, 3]]").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonArray>()
        value.size shouldBe 3
        val items = value.map { it.shouldBeInstanceOf<JsonNumber>().toLong() }
        items shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `materialize nested structure`() {
        val parser = SimdJsonParser()
        val json = """{"users": [{"name": "Alice", "age": 30}, {"name": "Bob", "age": 25}]}"""
        val doc = parser.iterate(json).getObject()
        val users = doc.findField("users").materialize().shouldBeInstanceOf<JsonArray>()
        users.size shouldBe 2

        val alice = users[0].shouldBeInstanceOf<JsonObject>()
        alice["name"].shouldBeInstanceOf<JsonString>()
        (alice["name"] as JsonString).value shouldBe "Alice"
    }

    // --- Parser reuse ---

    @Test
    fun `parser reuse`() {
        val parser = SimdJsonParser()

        val doc1 = parser.iterate("[1, 2]")
        doc1.getArray().map { it.getLong() } shouldBe listOf(1L, 2L)

        val doc2 = parser.iterate("""{"x": 10}""")
        doc2.getObject().findField("x").getLong() shouldBe 10L

        val doc3 = parser.iterate("true")
        doc3.getBoolean() shouldBe true
    }

    // --- Edge cases ---

    @Test
    fun `object with nested object values`() {
        val parser = SimdJsonParser()
        val json = """{"a": {"x": 1}, "b": {"y": 2}, "c": {"z": 3}}"""
        val obj = parser.iterate(json).getObject()
        val fields = mutableListOf<Pair<String, Long>>()
        for (field in obj) {
            val inner = field.value.getObject()
            for (innerField in inner) {
                fields.add(innerField.name to innerField.value.getLong())
            }
        }
        fields shouldBe listOf("x" to 1L, "y" to 2L, "z" to 3L)
    }

    @Test
    fun `large array`() {
        val parser = SimdJsonParser()
        val json = (0 until 100).joinToString(",", "[", "]")
        val arr = parser.iterate(json).getArray()
        val values = arr.map { it.getLong() }
        values shouldBe (0L until 100L).toList()
    }

    @Test
    fun `unicode string`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""["\u0041\u0042\u0043"]""")
        val value = doc.getArray().iterator().next().getString()
        value shouldBe "ABC"
    }

    @Test
    fun `escaped string`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""["hello \"world\""]""")
        val value = doc.getArray().iterator().next().getString()
        value shouldBe "hello \"world\""
    }

    @Test
    fun `empty string`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""[""]""")
        val value = doc.getArray().iterator().next().getString()
        value shouldBe ""
    }

    @Test
    fun `single field object`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"key": "value"}""").getObject()
        obj.findField("key").getString() shouldBe "value"
    }

    @Test
    fun `materialize empty containers`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[{}, []]").getArray()
        val iter = arr.iterator()

        val emptyObj = iter.next().materialize().shouldBeInstanceOf<JsonObject>()
        emptyObj.size shouldBe 0

        val emptyArr = iter.next().materialize().shouldBeInstanceOf<JsonArray>()
        emptyArr.size shouldBe 0
    }
}
