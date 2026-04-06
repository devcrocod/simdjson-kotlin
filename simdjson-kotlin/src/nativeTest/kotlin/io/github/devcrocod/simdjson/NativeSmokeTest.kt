package io.github.devcrocod.simdjson

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class NativeSmokeTest {

    // --- DOM API ---

    @Test
    fun domParseEmptyObject() {
        val parser = SimdJsonParser()
        val result = parser.parse("{}").shouldBeInstanceOf<JsonObject>()
        result.size shouldBe 0
        parser.close()
    }

    @Test
    fun domParseObject() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"name": "alice", "age": 30}""").shouldBeInstanceOf<JsonObject>()
        val name = result["name"].shouldBeInstanceOf<JsonString>()
        name.value shouldBe "alice"
        val age = result["age"].shouldBeInstanceOf<JsonNumber>()
        age.toLong() shouldBe 30L
        parser.close()
    }

    @Test
    fun domParseArray() {
        val parser = SimdJsonParser()
        val result = parser.parse("[1, 2, 3]").shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
        val first = result[0].shouldBeInstanceOf<JsonNumber>()
        first.toLong() shouldBe 1L
        parser.close()
    }

    @Test
    fun domParseScalars() {
        val parser = SimdJsonParser()

        parser.parse("42").shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L
        parser.parse("3.14").shouldBeInstanceOf<JsonNumber>().toDouble() shouldBe 3.14
        parser.parse("true").shouldBeInstanceOf<JsonBoolean>().value shouldBe true
        parser.parse("false").shouldBeInstanceOf<JsonBoolean>().value shouldBe false
        parser.parse("null") shouldBe JsonNull
        parser.parse(""""hello"""").shouldBeInstanceOf<JsonString>().value shouldBe "hello"

        parser.close()
    }

    @Test
    fun domParseNestedStructure() {
        val json = """{"users": [{"name": "bob", "scores": [10, 20]}, {"name": "carol", "scores": [30]}]}"""
        val parser = SimdJsonParser()
        val root = parser.parse(json).shouldBeInstanceOf<JsonObject>()
        val users = root["users"].shouldBeInstanceOf<JsonArray>()
        users.size shouldBe 2

        val bob = users[0].shouldBeInstanceOf<JsonObject>()
        bob["name"].shouldBeInstanceOf<JsonString>().value shouldBe "bob"
        val bobScores = bob["scores"].shouldBeInstanceOf<JsonArray>()
        bobScores.size shouldBe 2

        parser.close()
    }

    @Test
    fun domParseObjectIteration() {
        val parser = SimdJsonParser()
        val result = parser.parse("""{"a": 1, "b": 2, "c": 3}""").shouldBeInstanceOf<JsonObject>()

        val keys = mutableListOf<String>()
        val values = mutableListOf<Long>()
        for ((key, value) in result) {
            keys.add(key)
            values.add(value.shouldBeInstanceOf<JsonNumber>().toLong())
        }
        keys shouldBe listOf("a", "b", "c")
        values shouldBe listOf(1L, 2L, 3L)
        parser.close()
    }

    // --- On Demand API ---

    @Test
    fun onDemandScalarDocument() {
        val parser = SimdJsonParser()

        parser.iterate("42").use { it.getLong() shouldBe 42L }
        parser.iterate("3.14").use { it.getDouble() shouldBe 3.14 }
        parser.iterate("true").use { it.getBoolean() shouldBe true }
        parser.iterate(""""hello"""").use { it.getString() shouldBe "hello" }

        parser.close()
    }

    @Test
    fun onDemandObjectFieldAccess() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"name": "alice", "age": 30}""")
        val obj = doc.getObject()
        obj["name"].getString() shouldBe "alice"
        obj["age"].getLong() shouldBe 30L
        doc.close()
        parser.close()
    }

    @Test
    fun onDemandArrayIteration() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("[10, 20, 30]")
        val arr = doc.getArray()
        val values = mutableListOf<Long>()
        for (v in arr) {
            values.add(v.getLong())
        }
        values shouldBe listOf(10L, 20L, 30L)
        doc.close()
        parser.close()
    }

    @Test
    fun onDemandObjectIteration() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"x": 1, "y": 2}""")
        val obj = doc.getObject()
        val entries = mutableListOf<Pair<String, Long>>()
        for (field in obj) {
            entries.add(field.name to field.value.getLong())
        }
        entries shouldBe listOf("x" to 1L, "y" to 2L)
        doc.close()
        parser.close()
    }

    @Test
    fun onDemandMaterialize() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"items": [1, "two", true, null]}""")
        val obj = doc.getObject()
        val items = obj["items"].materialize().shouldBeInstanceOf<JsonArray>()
        items.size shouldBe 4
        items[0].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        items[1].shouldBeInstanceOf<JsonString>().value shouldBe "two"
        items[2].shouldBeInstanceOf<JsonBoolean>().value shouldBe true
        items[3] shouldBe JsonNull
        doc.close()
        parser.close()
    }

    @Test
    fun onDemandGetType() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"a": 1}""")
        doc.getType() shouldBe JsonType.OBJECT
        doc.close()
        parser.close()
    }

    @Test
    fun onDemandFindField() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"a": 1, "b": 2, "c": 3}""")
        val obj = doc.getObject()
        obj.findField("a").getLong() shouldBe 1L
        obj.findField("b").getLong() shouldBe 2L
        obj.findField("c").getLong() shouldBe 3L
        doc.close()
        parser.close()
    }

    @Test
    fun parserReuse() {
        val parser = SimdJsonParser()
        parser.parse("42").shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L
        parser.parse(""""hello"""").shouldBeInstanceOf<JsonString>().value shouldBe "hello"
        parser.iterate("[1]").use { doc ->
            val arr = doc.getArray()
            arr.iterator().next().getLong() shouldBe 1L
        }
        parser.close()
    }
}
