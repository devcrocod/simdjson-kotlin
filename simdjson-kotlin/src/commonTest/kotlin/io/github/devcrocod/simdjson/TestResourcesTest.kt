package io.github.devcrocod.simdjson

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class TestResourcesTest {

    @Test
    fun loadDemoJson() {
        val bytes = TestResources.load("simdjson-data/jsonexamples/small/demo.json")
        bytes.size shouldBe 387
        val parser = SimdJsonParser()
        val result = parser.parse(bytes)
        result.shouldBeInstanceOf<JsonObject>()
        parser.close()
    }

    @Test
    fun loadTwitterJson() {
        val bytes = TestResources.load("simdjson-data/jsonexamples/twitter.json")
        bytes.size.shouldBeGreaterThan(0)
        val parser = SimdJsonParser()
        val result = parser.parse(bytes)
        result.shouldBeInstanceOf<JsonObject>()
        parser.close()
    }

    @Test
    fun listMinefieldFiles() {
        val files = TestResources.listFiles("simdjson-data/jsonchecker/minefield")
        files.shouldNotBeEmpty()
        files.any { it.startsWith("y_") }.shouldBeTrue()
        files.any { it.startsWith("n_") }.shouldBeTrue()
    }

    @Test
    fun loadTextResource() {
        val text = TestResources.loadText("simdjson-data/jsonexamples/small/demo.json")
        text.shouldStartWith("{")
    }
}
