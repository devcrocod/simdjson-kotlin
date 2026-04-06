package io.github.devcrocod.simdjson.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.Test

class CollectionsTest {

    @Serializable
    data class WithList(val items: List<Int>)

    @Serializable
    data class WithStringList(val items: List<String>)

    @Serializable
    data class WithNestedList(val matrix: List<List<Int>>)

    @Serializable
    data class WithMap(val data: Map<String, Int>)

    @Serializable
    data class WithMapOfLists(val data: Map<String, List<Int>>)

    @Serializable
    data class Item(val id: Int, val name: String)

    @Serializable
    data class WithObjectList(val items: List<Item>)

    private val simdjson = SimdJson { }

    @Test
    fun `list of ints`() {
        val result = simdjson.decodeFromString<WithList>("""{"items": [1, 2, 3]}""")
        result shouldBe WithList(listOf(1, 2, 3))
    }

    @Test
    fun `list of strings`() {
        val result = simdjson.decodeFromString<WithStringList>("""{"items": ["a", "b", "c"]}""")
        result shouldBe WithStringList(listOf("a", "b", "c"))
    }

    @Test
    fun `empty list`() {
        val result = simdjson.decodeFromString<WithList>("""{"items": []}""")
        result shouldBe WithList(emptyList())
    }

    @Test
    fun `nested list`() {
        val result = simdjson.decodeFromString<WithNestedList>("""{"matrix": [[1, 2], [3, 4]]}""")
        result shouldBe WithNestedList(listOf(listOf(1, 2), listOf(3, 4)))
    }

    @Test
    fun `map of string to int`() {
        val result = simdjson.decodeFromString<WithMap>("""{"data": {"a": 1, "b": 2}}""")
        result shouldBe WithMap(mapOf("a" to 1, "b" to 2))
    }

    @Test
    fun `empty map`() {
        val result = simdjson.decodeFromString<WithMap>("""{"data": {}}""")
        result shouldBe WithMap(emptyMap())
    }

    @Test
    fun `map of string to list`() {
        val result = simdjson.decodeFromString<WithMapOfLists>("""{"data": {"x": [1, 2], "y": [3]}}""")
        result shouldBe WithMapOfLists(mapOf("x" to listOf(1, 2), "y" to listOf(3)))
    }

    @Test
    fun `list of objects`() {
        val result = simdjson.decodeFromString<WithObjectList>(
            """{"items": [{"id": 1, "name": "a"}, {"id": 2, "name": "b"}]}"""
        )
        result shouldBe WithObjectList(listOf(Item(1, "a"), Item(2, "b")))
    }
}
