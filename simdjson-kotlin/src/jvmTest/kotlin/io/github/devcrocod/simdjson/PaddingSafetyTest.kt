package io.github.devcrocod.simdjson

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/**
 * Verifies that parsing buffers with NO trailing padding (buffer.size == data length)
 * works correctly and does not crash.  SIMD parsing requires 64 bytes of padding
 * beyond the input; these tests confirm the library handles it internally.
 */
class PaddingSafetyTest {

    // -- helpers --

    /** Returns a [ByteArray] whose size equals exactly [json].length — zero spare bytes. */
    private fun tightBuffer(json: String): ByteArray {
        val bytes = json.encodeToByteArray()
        // encodeToByteArray already returns an exact-length array,
        // but be explicit to make the test intention clear.
        require(bytes.size == json.encodeToByteArray().size)
        return bytes
    }

    // -----------------------------------------------------------------------
    // DOM parse — unpadded buffer
    // -----------------------------------------------------------------------

    @Test
    fun `DOM parse object from unpadded buffer`() {
        val json = """{"name": "alice", "age": 30}"""
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        val result = parser.parse(buf, buf.size).shouldBeInstanceOf<JsonObject>()
        result["name"].shouldBeInstanceOf<JsonString>().value shouldBe "alice"
        result["age"].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 30L
        parser.close()
    }

    @Test
    fun `DOM parse array from unpadded buffer`() {
        val json = "[1, 2, 3]"
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        val result = parser.parse(buf, buf.size).shouldBeInstanceOf<JsonArray>()
        result.size shouldBe 3
        result[0].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        result[2].shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 3L
        parser.close()
    }

    @Test
    fun `DOM parse scalars from unpadded buffer`() {
        val parser = SimdJsonParser()
        for ((json, check) in listOf<Pair<String, (JsonValue) -> Unit>>(
            "42" to { it.shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 42L },
            "3.14" to { it.shouldBeInstanceOf<JsonNumber>().toDouble() shouldBe 3.14 },
            "true" to { it.shouldBeInstanceOf<JsonBoolean>().value shouldBe true },
            "null" to { it shouldBe JsonNull },
            """"hello"""" to { it.shouldBeInstanceOf<JsonString>().value shouldBe "hello" },
        )) {
            val buf = tightBuffer(json)
            check(parser.parse(buf, buf.size))
        }
        parser.close()
    }

    @Test
    fun `DOM parse deeply nested JSON from unpadded buffer`() {
        val json = """{"users":[{"name":"bob","scores":[10,20]},{"name":"carol","scores":[30]}]}"""
        val buf = tightBuffer(json)
        val parser = SimdJsonParser()
        val root = parser.parse(buf, buf.size).shouldBeInstanceOf<JsonObject>()
        val users = root["users"].shouldBeInstanceOf<JsonArray>()
        users.size shouldBe 2
        val bob = users[0].shouldBeInstanceOf<JsonObject>()
        bob["name"].shouldBeInstanceOf<JsonString>().value shouldBe "bob"
        parser.close()
    }

    // -----------------------------------------------------------------------
    // On-Demand iterate — unpadded buffer
    // -----------------------------------------------------------------------

    @Test
    fun `On-Demand iterate object from unpadded buffer`() {
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
    fun `On-Demand iterate array from unpadded buffer`() {
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
    fun `On-Demand iterate scalar from unpadded buffer`() {
        val parser = SimdJsonParser()
        val buf = tightBuffer("42")
        parser.iterate(buf, buf.size).use { doc ->
            doc.getLong() shouldBe 42L
        }
        parser.close()
    }

    // -----------------------------------------------------------------------
    // Edge case: buffer smaller than SIMDJSON_PADDING
    // -----------------------------------------------------------------------

    @Test
    fun `parse tiny JSON shorter than SIMDJSON_PADDING`() {
        val parser = SimdJsonParser()
        // "1" is only 1 byte — far below 64-byte SIMDJSON_PADDING
        val buf = tightBuffer("1")
        parser.parse(buf, buf.size).shouldBeInstanceOf<JsonNumber>().toLong() shouldBe 1L
        parser.iterate(buf, buf.size).use { doc ->
            doc.getLong() shouldBe 1L
        }
        parser.close()
    }
}
