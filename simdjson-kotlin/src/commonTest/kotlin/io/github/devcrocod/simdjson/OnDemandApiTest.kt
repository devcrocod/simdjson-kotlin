package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class OnDemandApiTest {

    // =====================================================================
    // Forward-only iteration
    // =====================================================================

    @Test
    fun `array iterator - next past end throws NoSuchElementException`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[1, 2]").getArray()
        val iter = arr.iterator()
        iter.next().getLong() shouldBe 1L
        iter.next().getLong() shouldBe 2L
        iter.hasNext() shouldBe false
        shouldThrow<NoSuchElementException> { iter.next() }
    }

    @Test
    fun `object iterator - next past end throws NoSuchElementException`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1}""").getObject()
        val iter = obj.iterator()
        val field = iter.next()
        field.name shouldBe "a"
        field.value.getLong() shouldBe 1L
        iter.hasNext() shouldBe false
        shouldThrow<NoSuchElementException> { iter.next() }
    }

    @Test
    fun `array - skip nested containers then read last`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"x": 1}, [10, 20], 42]""").getArray()
        val iter = arr.iterator()
        iter.next() // skip object
        iter.next() // skip array
        iter.next().getLong() shouldBe 42L
        iter.hasNext() shouldBe false
    }

    @Test
    fun `object - skip nested containers then read last`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": [1, 2], "b": {"x": 1}, "c": 42}""").getObject()
        val iter = obj.iterator()
        iter.next() // skip "a"
        iter.next() // skip "b"
        val field = iter.next()
        field.name shouldBe "c"
        field.value.getLong() shouldBe 42L
    }

    @Test
    fun `nested arrays - iterate outer and inner sequentially`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[1, 2], [3, 4], [5, 6]]").getArray()
        val result = mutableListOf<List<Long>>()
        for (elem in arr) {
            result.add(elem.getArray().map { it.getLong() })
        }
        result shouldBe listOf(listOf(1L, 2L), listOf(3L, 4L), listOf(5L, 6L))
    }

    @Test
    fun `nested objects - iterate outer and inner sequentially`() {
        val parser = SimdJsonParser()
        val json = """{"a": {"x": 1, "y": 2}, "b": {"x": 3, "y": 4}}"""
        val obj = parser.iterate(json).getObject()
        val result = mutableMapOf<String, Map<String, Long>>()
        for (field in obj) {
            val inner = mutableMapOf<String, Long>()
            for (f in field.value.getObject()) {
                inner[f.name] = f.value.getLong()
            }
            result[field.name] = inner
        }
        result shouldBe mapOf(
            "a" to mapOf("x" to 1L, "y" to 2L),
            "b" to mapOf("x" to 3L, "y" to 4L)
        )
    }

    @Test
    fun `array - partial consumption of inner array then continue`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[1, 2, 3], [4, 5, 6]]").getArray()
        val iter = arr.iterator()
        // Read only first element of first sub-array
        val inner = iter.next().getArray().iterator()
        inner.next().getLong() shouldBe 1L
        // Don't consume the rest — auto-skip should handle it
        // Read second sub-array fully
        val second = iter.next().getArray().map { it.getLong() }
        second shouldBe listOf(4L, 5L, 6L)
    }

    @Test
    fun `object - partial consumption of inner object then continue`() {
        val parser = SimdJsonParser()
        val json = """{"a": {"x": 1, "y": 2}, "b": 42}"""
        val obj = parser.iterate(json).getObject()
        val iter = obj.iterator()
        // Start iterating inner object but don't finish
        val inner = iter.next().value.getObject().iterator()
        inner.next() // read "x" only
        // Auto-skip should handle "y"
        val field = iter.next()
        field.name shouldBe "b"
        field.value.getLong() shouldBe 42L
    }

    @Test
    fun `array of objects - access one field from each`() {
        val parser = SimdJsonParser()
        val json = """[{"name": "Alice", "age": 30}, {"name": "Bob", "age": 25}, {"name": "Carol", "age": 35}]"""
        val names = parser.iterate(json).getArray().map {
            it.getObject().findField("name").getString()
            // "age" is auto-skipped
        }
        names shouldBe listOf("Alice", "Bob", "Carol")
    }

    // =====================================================================
    // JsonIterationException on repeated access
    // =====================================================================

    @Test
    fun `value - getLong then getString throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[1]").getArray()
        val value = arr.iterator().next()
        value.getLong()
        shouldThrow<JsonIterationException> { value.getString() }
    }

    @Test
    fun `value - getString then getDouble throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""["hi"]""").getArray()
        val value = arr.iterator().next()
        value.getString()
        shouldThrow<JsonIterationException> { value.getDouble() }
    }

    @Test
    fun `value - getObject then getLong throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{}]""").getArray()
        val value = arr.iterator().next()
        value.getObject()
        shouldThrow<JsonIterationException> { value.getLong() }
    }

    @Test
    fun `value - getArray then getString throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[]]").getArray()
        val value = arr.iterator().next()
        value.getArray()
        shouldThrow<JsonIterationException> { value.getString() }
    }

    @Test
    fun `value - materialize then getLong throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val value = arr.iterator().next()
        value.materialize()
        shouldThrow<JsonIterationException> { value.getLong() }
    }

    @Test
    fun `value - getLong then materialize throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val value = arr.iterator().next()
        value.getLong()
        shouldThrow<JsonIterationException> { value.materialize() }
    }

    @Test
    fun `value - getBoolean then getBoolean throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[true]").getArray()
        val value = arr.iterator().next()
        value.getBoolean()
        shouldThrow<JsonIterationException> { value.getBoolean() }
    }

    @Test
    fun `value - getType does not consume then getLong works`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val value = arr.iterator().next()
        value.getType() shouldBe JsonType.NUMBER
        value.getLong() shouldBe 42L
    }

    @Test
    fun `value - isNull on non-null does not consume then getLong works`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val value = arr.iterator().next()
        value.isNull() shouldBe false
        value.getLong() shouldBe 42L
    }

    @Test
    fun `value - isNull on null consumes`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[null]").getArray()
        val value = arr.iterator().next()
        value.isNull() shouldBe true
        shouldThrow<JsonIterationException> { value.isNull() }
    }

    @Test
    fun `document - getObject then getArray throws`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("{}")
        doc.getObject()
        shouldThrow<JsonIterationException> { doc.getArray() }
    }

    @Test
    fun `document - getString then getBoolean throws`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate(""""hello"""")
        doc.getString()
        shouldThrow<JsonIterationException> { doc.getBoolean() }
    }

    @Test
    fun `document - getLong then getType throws`() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("42")
        doc.getLong()
        shouldThrow<JsonIterationException> { doc.getType() }
    }

    @Test
    fun `object - findField after iterator consumed throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2}""").getObject()
        for (f in obj) {
            f.value.getLong()
        } // exhaust iterator
        shouldThrow<JsonIterationException> { obj.findField("a") }
    }

    @Test
    fun `object - get after iterator consumed throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2}""").getObject()
        for (f in obj) {
            f.value.getLong()
        }
        shouldThrow<JsonIterationException> { obj["a"] }
    }

    @Test
    fun `findField - after exhausting all fields next findField throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1}""").getObject()
        obj.findField("a").getLong()
        // Object had one field; trying another forward search should fail
        shouldThrow<SimdJsonException> { obj.findField("b") }
    }

    @Test
    fun `array - forEach fully consumes then iterator throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[1, 2, 3]").getArray()
        arr.forEach { it.getLong() }
        shouldThrow<JsonIterationException> { arr.iterator() }
    }

    // =====================================================================
    // findField vs get (ordered vs unordered)
    // =====================================================================

    @Test
    fun `findField - sequential access works`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        obj.findField("a").getLong() shouldBe 1L
        obj.findField("b").getLong() shouldBe 2L
        obj.findField("c").getLong() shouldBe 3L
    }

    @Test
    fun `findField - skip middle field works`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        obj.findField("a").getLong() shouldBe 1L
        // Skip "b", go directly to "c"
        obj.findField("c").getLong() shouldBe 3L
    }

    @Test
    fun `findField - reverse order throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        obj.findField("b").getLong() shouldBe 2L
        // "a" is before "b" — forward-only can't find it
        shouldThrow<SimdJsonException> { obj.findField("a") }
    }

    @Test
    fun `findField - not found throws JsonParsingException`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1}""").getObject()
        shouldThrow<JsonParsingException> { obj.findField("z") }
    }

    @Test
    fun `findField - empty object throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("{}").getObject()
        shouldThrow<JsonParsingException> { obj.findField("a") }
    }

    @Test
    fun `get - reverse order works wrapping`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3}""").getObject()
        obj["c"].getLong() shouldBe 3L
        // "a" is before "c" — get wraps around and finds it
        obj["a"].getLong() shouldBe 1L
    }

    @Test
    fun `get - access last then first`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"first": 10, "middle": 20, "last": 30}""").getObject()
        obj["last"].getLong() shouldBe 30L
        obj["first"].getLong() shouldBe 10L
    }

    @Test
    fun `get - arbitrary order access`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2, "c": 3, "d": 4}""").getObject()
        obj["c"].getLong() shouldBe 3L
        obj["a"].getLong() shouldBe 1L
        obj["d"].getLong() shouldBe 4L
        obj["b"].getLong() shouldBe 2L
    }

    @Test
    fun `get - not found throws JsonParsingException`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"a": 1, "b": 2}""").getObject()
        shouldThrow<JsonParsingException> { obj["z"] }
    }

    @Test
    fun `get - empty object throws`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("{}").getObject()
        shouldThrow<JsonParsingException> { obj["a"] }
    }

    @Test
    fun `findField - nested object values`() {
        val parser = SimdJsonParser()
        val json = """{"config": {"host": "localhost", "port": 8080}, "name": "app"}"""
        val obj = parser.iterate(json).getObject()
        val config = obj.findField("config").getObject()
        config.findField("host").getString() shouldBe "localhost"
        config.findField("port").getLong() shouldBe 8080L
        obj.findField("name").getString() shouldBe "app"
    }

    @Test
    fun `get - nested object values`() {
        val parser = SimdJsonParser()
        val json = """{"name": "app", "config": {"port": 8080, "host": "localhost"}}"""
        val obj = parser.iterate(json).getObject()
        val config = obj["config"].getObject()
        // Access in reverse order
        config["host"].getString() shouldBe "localhost"
    }

    @Test
    fun `findField - value with nested container auto-skipped`() {
        val parser = SimdJsonParser()
        val json = """{"a": [1, 2, 3], "b": {"x": true}, "c": 42}"""
        val obj = parser.iterate(json).getObject()
        // Skip "a" (array) and "b" (object) by jumping to "c"
        obj.findField("c").getLong() shouldBe 42L
    }

    @Test
    fun `findField - single field object`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"only": 99}""").getObject()
        obj.findField("only").getLong() shouldBe 99L
    }

    @Test
    fun `get - single field object`() {
        val parser = SimdJsonParser()
        val obj = parser.iterate("""{"only": 99}""").getObject()
        obj["only"].getLong() shouldBe 99L
    }

    // =====================================================================
    // materialize()
    // =====================================================================

    @Test
    fun `materialize - mixed type array`() {
        val parser = SimdJsonParser()
        val json = """[42, "hello", true, null, 3.14, {"a": 1}, [1, 2]]"""
        val arr = parser.iterate(json).getArray()
        val iter = arr.iterator()

        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L
        iter.next().materialize().shouldBeInstanceOf<JsonString>().value shouldBe "hello"
        iter.next().materialize().shouldBeInstanceOf<JsonBoolean>().value shouldBe true
        iter.next().materialize().shouldBeInstanceOf<JsonNull>()
        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toDouble() shouldBe 3.14
        iter.next().materialize().shouldBeInstanceOf<JsonObject>().size shouldBe 1
        iter.next().materialize().shouldBeInstanceOf<JsonArray>().size shouldBe 2
    }

    @Test
    fun `materialize - preserves object field order`() {
        val parser = SimdJsonParser()
        val json = """{"z": 1, "a": 2, "m": 3, "b": 4}"""
        val obj = parser.iterate(json).getObject()
        val materialized = obj.findField("z") // consume first value to trigger iterate
        // Can't materialize partial object via findField, let's use a full approach
        val parser2 = SimdJsonParser()
        val arr = parser2.iterate("""[{"z": 1, "a": 2, "m": 3, "b": 4}]""").getArray()
        val value = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        value.keys().toList() shouldBe listOf("z", "a", "m", "b")
    }

    @Test
    fun `materialize - deep nesting`() {
        val parser = SimdJsonParser()
        val json = """[{"level1": {"level2": {"level3": [1, 2, 3]}}}]"""
        val arr = parser.iterate(json).getArray()
        val root = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        val l1 = root["level1"].shouldBeInstanceOf<JsonObject>()
        val l2 = l1["level2"].shouldBeInstanceOf<JsonObject>()
        val l3 = l2["level3"].shouldBeInstanceOf<JsonArray>()
        l3.size shouldBe 3
        l3[0].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        l3[1].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 2L
        l3[2].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 3L
    }

    @Test
    fun `materialize - then access throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"a": 1}]""").getArray()
        val value = arr.iterator().next()
        value.materialize()
        shouldThrow<JsonIterationException> { value.getObject() }
    }

    @Test
    fun `materialize - double materialize throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val value = arr.iterator().next()
        value.materialize()
        shouldThrow<JsonIterationException> { value.materialize() }
    }

    @Test
    fun `materialize - one field then continue iterating`() {
        val parser = SimdJsonParser()
        val json = """[{"name": "Alice", "scores": [90, 85]}, {"name": "Bob", "scores": [70, 95]}]"""
        val arr = parser.iterate(json).getArray()
        val results = mutableListOf<Pair<String, JsonArray>>()
        for (elem in arr) {
            val obj = elem.getObject()
            val name = obj.findField("name").getString()
            val scores = obj.findField("scores").materialize().shouldBeInstanceOf<JsonArray>()
            results.add(name to scores)
        }
        results[0].first shouldBe "Alice"
        results[0].second.size shouldBe 2
        results[0].second[0].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 90L

        results[1].first shouldBe "Bob"
        results[1].second[1].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 95L
    }

    @Test
    fun `materialize - unicode strings`() {
        val parser = SimdJsonParser()
        val json = """["\u0041\u0042\u0043", "\u00e9\u00e8\u00ea"]"""
        val arr = parser.iterate(json).getArray()
        val iter = arr.iterator()
        iter.next().materialize().shouldBeInstanceOf<JsonString>().value shouldBe "ABC"
        iter.next().materialize().shouldBeInstanceOf<JsonString>().value shouldBe "\u00e9\u00e8\u00ea"
    }

    @Test
    fun `materialize - special number values`() {
        val parser = SimdJsonParser()
        val json = "[0, -1, 9999999999, -0.0, 1.0e10]"
        val arr = parser.iterate(json).getArray()
        val iter = arr.iterator()

        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 0L
        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toLong() shouldBe -1L
        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 9999999999L
        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toDouble() shouldBe -0.0
        iter.next().materialize().shouldBeInstanceOf<JsonNumber>().toDouble() shouldBe 1.0e10
    }

    // --- Materialized JsonObject API ---

    @Test
    fun `materialized JsonObject - get returns value or null`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"a": 1, "b": 2}]""").getArray()
        val obj = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        obj["a"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        obj["b"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 2L
        obj["z"] shouldBe null
    }

    @Test
    fun `materialized JsonObject - keys in insertion order`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"c": 3, "a": 1, "b": 2}]""").getArray()
        val obj = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        obj.keys().toList() shouldBe listOf("c", "a", "b")
    }

    @Test
    fun `materialized JsonObject - contains`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"x": 1}]""").getArray()
        val obj = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        ("x" in obj) shouldBe true
        ("y" in obj) shouldBe false
    }

    @Test
    fun `materialized JsonObject - iterate entries`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"a": 1, "b": 2}]""").getArray()
        val obj = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        val entries = obj.map { (k, v) -> k to v.shouldBeInstanceOf<JsonNumber>().toLong() }
        entries shouldBe listOf("a" to 1L, "b" to 2L)
    }

    @Test
    fun `materialized JsonObject - size`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("""[{"a": 1, "b": 2, "c": 3}]""").getArray()
        val obj = arr.iterator().next().materialize().shouldBeInstanceOf<JsonObject>()
        obj.size shouldBe 3
    }

    // --- Materialized JsonArray API ---

    @Test
    fun `materialized JsonArray - indexed access`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[10, 20, 30]]").getArray()
        val inner = arr.iterator().next().materialize().shouldBeInstanceOf<JsonArray>()
        inner[0].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 10L
        inner[1].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 20L
        inner[2].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 30L
    }

    @Test
    fun `materialized JsonArray - index out of bounds`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[1]]").getArray()
        val inner = arr.iterator().next().materialize().shouldBeInstanceOf<JsonArray>()
        shouldThrow<IndexOutOfBoundsException> { inner[5] }
        shouldThrow<IndexOutOfBoundsException> { inner[-1] }
    }

    @Test
    fun `materialized JsonArray - size and iterate`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[[1, 2, 3]]").getArray()
        val inner = arr.iterator().next().materialize().shouldBeInstanceOf<JsonArray>()
        inner.size shouldBe 3
        inner.map { it.shouldBeInstanceOf<JsonNumber>().toLong() } shouldBe listOf(1L, 2L, 3L)
    }

    // --- Materialized JsonNumber API ---

    @Test
    fun `materialized JsonNumber - integer properties`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[42]").getArray()
        val num = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        num.isInteger shouldBe true
        num.isLong shouldBe true
        num.toLong() shouldBe 42L
        num.toInt() shouldBe 42
        num.toDouble() shouldBe 42.0
    }

    @Test
    fun `materialized JsonNumber - double properties`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[3.14]").getArray()
        val num = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        num.isInteger shouldBe false
        num.isLong shouldBe false
        num.toDouble() shouldBe 3.14
        shouldThrow<JsonTypeException> { num.toLong() }
    }

    @Test
    fun `materialized JsonNumber - toULong`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[100]").getArray()
        val num = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        num.toULong() shouldBe 100.toULong()
    }

    @Test
    fun `materialized JsonNumber - toULong negative throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[-1]").getArray()
        val num = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        shouldThrow<JsonParsingException> { num.toULong() }
    }

    @Test
    fun `materialized JsonNumber - toInt overflow throws`() {
        val parser = SimdJsonParser()
        val arr = parser.iterate("[9999999999]").getArray()
        val num = arr.iterator().next().materialize().shouldBeInstanceOf<JsonNumber>()
        shouldThrow<JsonParsingException> { num.toInt() }
    }
}
