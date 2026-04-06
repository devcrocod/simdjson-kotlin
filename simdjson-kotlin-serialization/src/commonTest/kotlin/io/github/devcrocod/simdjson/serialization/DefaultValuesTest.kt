package io.github.devcrocod.simdjson.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class DefaultValuesTest {

    @Serializable
    data class WithDefaults(
        val required: String,
        val optional: String = "default",
        val count: Int = 0
    )

    @Serializable
    data class AllOptional(
        val a: String = "x",
        val b: Int = 42
    )

    private val simdjson = SimdJson { }

    @Test
    fun `missing optional field uses default`() {
        val result = simdjson.decodeFromString<WithDefaults>("""{"required": "hello"}""")
        result shouldBe WithDefaults("hello", "default", 0)
    }

    @Test
    fun `provided optional field overrides default`() {
        val result = simdjson.decodeFromString<WithDefaults>(
            """{"required": "hello", "optional": "custom", "count": 5}"""
        )
        result shouldBe WithDefaults("hello", "custom", 5)
    }

    @Test
    fun `all optional fields missing`() {
        val result = simdjson.decodeFromString<AllOptional>("""{}""")
        result shouldBe AllOptional("x", 42)
    }

    @Test
    fun `partial optional fields`() {
        val result = simdjson.decodeFromString<AllOptional>("""{"b": 100}""")
        result shouldBe AllOptional("x", 100)
    }
}
