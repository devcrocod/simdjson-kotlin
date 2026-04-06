package io.github.devcrocod.simdjson.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import kotlin.test.Test

class SimdJsonTest {

    @Serializable
    data class User(val name: String, val age: Int, val tags: List<String>)

    @Serializable
    data class Simple(val x: Int, val y: String)

    private val simdjson = SimdJson { }

    @Test
    fun `decode simple class from string`() {
        val result = simdjson.decodeFromString<Simple>("""{"x": 42, "y": "hello"}""")
        result shouldBe Simple(42, "hello")
    }

    @Test
    fun `decode class with list from string`() {
        val result = simdjson.decodeFromString<User>(
            """{"name": "Alice", "age": 30, "tags": ["admin", "user"]}"""
        )
        result shouldBe User("Alice", 30, listOf("admin", "user"))
    }

    @Test
    fun `decode from byte array`() {
        val bytes = """{"x": 1, "y": "test"}""".encodeToByteArray()
        val result = simdjson.decodeFromByteArray(serializer<Simple>(), bytes)
        result shouldBe Simple(1, "test")
    }

    @Test
    fun `encodeToString throws UnsupportedOperationException`() {
        shouldThrow<UnsupportedOperationException> {
            simdjson.encodeToString(serializer<Simple>(), Simple(1, "a"))
        }
    }

    @Test
    fun `builder DSL configures ignoreUnknownKeys`() {
        val sj = SimdJson { ignoreUnknownKeys = true }
        sj.configuration.ignoreUnknownKeys shouldBe true
        sj.configuration.isLenient shouldBe false
        sj.configuration.coerceInputValues shouldBe false
    }

    @Test
    fun `builder DSL from existing instance`() {
        val base = SimdJson { ignoreUnknownKeys = true }
        val derived = SimdJson(from = base) { coerceInputValues = true }
        derived.configuration.ignoreUnknownKeys shouldBe true
        derived.configuration.coerceInputValues shouldBe true
    }

    @Test
    fun `field order in JSON does not matter`() {
        val result = simdjson.decodeFromString<Simple>("""{"y": "world", "x": 99}""")
        result shouldBe Simple(99, "world")
    }
}
