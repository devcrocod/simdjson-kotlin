package io.github.devcrocod.simdjson.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class NullabilityTest {

    @Serializable
    data class WithNullable(val name: String, val bio: String? = null)

    @Serializable
    data class WithCoercible(val name: String, val count: Int = 0)

    private val simdjson = SimdJson { }
    private val coercing = SimdJson { coerceInputValues = true }

    @Test
    fun `nullable field with value`() {
        val result = simdjson.decodeFromString<WithNullable>("""{"name": "Alice", "bio": "hello"}""")
        result shouldBe WithNullable("Alice", "hello")
    }

    @Test
    fun `nullable field with null`() {
        val result = simdjson.decodeFromString<WithNullable>("""{"name": "Alice", "bio": null}""")
        result shouldBe WithNullable("Alice", null)
    }

    @Test
    fun `nullable field missing`() {
        val result = simdjson.decodeFromString<WithNullable>("""{"name": "Alice"}""")
        result shouldBe WithNullable("Alice", null)
    }

    @Test
    fun `coerceInputValues converts null to default for non-nullable`() {
        val result = coercing.decodeFromString<WithCoercible>("""{"name": "Alice", "count": null}""")
        result shouldBe WithCoercible("Alice", 0)
    }

    @Test
    fun `without coercion null for non-nullable throws`() {
        shouldThrow<Exception> {
            simdjson.decodeFromString<WithCoercible>("""{"name": "Alice", "count": null}""")
        }
    }
}
