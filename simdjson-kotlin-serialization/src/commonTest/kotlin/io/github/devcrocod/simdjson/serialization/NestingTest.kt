package io.github.devcrocod.simdjson.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class NestingTest {

    @Serializable
    data class Address(val city: String, val zip: String)

    @Serializable
    data class Person(val name: String, val address: Address)

    @Serializable
    data class Company(val name: String, val employees: List<Person>)

    @Serializable
    data class Wrapper(val inner: Inner)

    @Serializable
    data class Inner(val value: Int)

    private val simdjson = SimdJson { }

    @Test
    fun `nested object`() {
        val json = """{"name": "Alice", "address": {"city": "NYC", "zip": "10001"}}"""
        val result = simdjson.decodeFromString<Person>(json)
        result shouldBe Person("Alice", Address("NYC", "10001"))
    }

    @Test
    fun `deeply nested`() {
        val json = """{"inner": {"value": 42}}"""
        val result = simdjson.decodeFromString<Wrapper>(json)
        result shouldBe Wrapper(Inner(42))
    }

    @Test
    fun `list of objects`() {
        val json = """{"name": "Acme", "employees": [
            {"name": "Alice", "address": {"city": "NYC", "zip": "10001"}},
            {"name": "Bob", "address": {"city": "LA", "zip": "90001"}}
        ]}"""
        val result = simdjson.decodeFromString<Company>(json)
        result shouldBe Company(
            "Acme",
            listOf(
                Person("Alice", Address("NYC", "10001")),
                Person("Bob", Address("LA", "90001"))
            )
        )
    }

    @Test
    fun `object containing list of primitives`() {
        val json = """{"name": "Alice", "address": {"city": "NYC", "zip": "10001"}}"""
        val result = simdjson.decodeFromString<Person>(json)
        result.address.city shouldBe "NYC"
    }
}
