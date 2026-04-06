package io.github.devcrocod.simdjson

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/**
 * Verifies that parsing buffers with NO trailing padding works correctly
 * through the Native/cinterop path on macOS.
 */
class NativePaddingSafetyTest {

    private fun tightBuffer(json: String): ByteArray = json.encodeToByteArray()

    // -- DOM parse --

    @Test
    fun domParseObjectFromUnpaddedBuffer() {
        val json = """{"name": "alice", "age": 30}"""
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        val result = parser.parse(buf, buf.size).shouldBeInstanceOf<JsonObject>()
        result["name"].shouldBeInstanceOf<JsonString>().value shouldBe "alice"
        result["age"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 30L
        parser.close()
    }

    @Test
    fun domParseArrayFromUnpaddedBuffer() {
        val json = "[1, 2, 3]"
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        val result = parser.parse(buf, buf.size).shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
        result[0].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        parser.close()
    }

    @Test
    fun domParseScalarFromUnpaddedBuffer() {
        val parser = SimdJsonParser()
        val buf = tightBuffer("42")
        parser.parse(buf, buf.size).shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L
        parser.close()
    }

    @Test
    fun domParseDeeplyNestedFromUnpaddedBuffer() {
        val json = """{"users":[{"name":"bob","scores":[10,20]},{"name":"carol","scores":[30]}]}"""
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        val root = parser.parse(buf, buf.size).shouldBeInstanceOf<JsonObject>()
        val users = root["users"].shouldBeInstanceOf<JsonArray>()
        users.size shouldBe 2
        parser.close()
    }

    // -- On-Demand iterate --

    @Test
    fun onDemandIterateObjectFromUnpaddedBuffer() {
        val json = """{"name": "alice", "age": 30}"""
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        parser.iterate(buf, buf.size).use { doc ->
            val obj = doc.getObject()
            obj["name"].getString() shouldBe "alice"
            obj["age"].getLong() shouldBe 30L
        }
        parser.close()
    }

    @Test
    fun onDemandIterateArrayFromUnpaddedBuffer() {
        val json = "[10, 20, 30]"
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        parser.iterate(buf, buf.size).use { doc ->
            val arr = doc.getArray()
            val values = mutableListOf<Long>()
            for (v in arr) { values.add(v.getLong()) }
            values shouldBe listOf(10L, 20L, 30L)
        }
        parser.close()
    }

    @Test
    fun onDemandIterateScalarFromUnpaddedBuffer() {
        val parser = SimdJsonParser()
        val buf = tightBuffer("42")
        parser.iterate(buf, buf.size).use { doc ->
            doc.getLong() shouldBe 42L
        }
        parser.close()
    }

    // -- Edge case --

    @Test
    fun parseTinyJsonShorterThanPadding() {
        val parser = SimdJsonParser()
        val buf = tightBuffer("1")
        parser.parse(buf, buf.size).shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        parser.iterate(buf, buf.size).use { doc ->
            doc.getLong() shouldBe 1L
        }
        parser.close()
    }
}
