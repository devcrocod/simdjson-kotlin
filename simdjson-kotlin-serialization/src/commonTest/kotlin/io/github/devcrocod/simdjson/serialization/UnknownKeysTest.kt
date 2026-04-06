package io.github.devcrocod.simdjson.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class UnknownKeysTest {

    @Serializable
    data class Strict(val a: Int)

    private val strict = SimdJson { }
    private val lenient = SimdJson { ignoreUnknownKeys = true }

    @Test
    fun `unknown key throws by default`() {
        val ex = shouldThrow<SimdJsonDecodingException> {
            strict.decodeFromString<Strict>("""{"a": 1, "b": 2}""")
        }
        ex.message shouldContain "unknown key 'b'"
    }

    @Test
    fun `unknown key ignored with ignoreUnknownKeys`() {
        val result = lenient.decodeFromString<Strict>("""{"a": 1, "b": 2, "c": 3}""")
        result shouldBe Strict(1)
    }

    @Test
    fun `no unknown keys passes strict mode`() {
        val result = strict.decodeFromString<Strict>("""{"a": 1}""")
        result shouldBe Strict(1)
    }
}
