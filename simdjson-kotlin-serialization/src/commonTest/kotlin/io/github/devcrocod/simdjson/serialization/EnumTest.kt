package io.github.devcrocod.simdjson.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class EnumTest {

    @Serializable
    enum class Color { RED, GREEN, BLUE }

    @Serializable
    enum class Status {
        @SerialName("active") ACTIVE,
        @SerialName("inactive") INACTIVE
    }

    @Serializable
    data class WithEnum(val color: Color)

    @Serializable
    data class WithSerialNameEnum(val status: Status)

    private val simdjson = SimdJson { }

    @Test
    fun `decode enum value`() {
        val result = simdjson.decodeFromString<WithEnum>("""{"color": "RED"}""")
        result shouldBe WithEnum(Color.RED)
    }

    @Test
    fun `decode enum with SerialName`() {
        val result = simdjson.decodeFromString<WithSerialNameEnum>("""{"status": "active"}""")
        result shouldBe WithSerialNameEnum(Status.ACTIVE)
    }

    @Test
    fun `unknown enum value throws`() {
        val ex = shouldThrow<SimdJsonDecodingException> {
            simdjson.decodeFromString<WithEnum>("""{"color": "PURPLE"}""")
        }
        ex.message shouldContain "PURPLE"
    }
}
