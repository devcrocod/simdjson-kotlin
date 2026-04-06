package io.github.devcrocod.simdjson

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class CorpusParsingTest {

    @Test
    fun `all corpus files parse successfully`() {
        val files = TestResources.listFiles("simdjson-data/jsonexamples")
            .filter { it.endsWith(".json") }
        check(files.isNotEmpty()) {
            "No .json files found in simdjson-data/jsonexamples. " +
                "Clone https://github.com/simdjson/simdjson-data into testdata/simdjson-data"
        }
        for (file in files) {
            val bytes = TestResources.load("simdjson-data/jsonexamples/$file")
            SimdJsonParser().parse(bytes, bytes.size)
        }
    }

    @Test
    fun `twitter json parses as JsonObject`() {
        val bytes = TestResources.load("simdjson-data/jsonexamples/twitter.json")
        SimdJsonParser().parse(bytes, bytes.size).shouldBeInstanceOf<JsonObject>()
    }

    @Test
    fun `github_events json parses as JsonArray`() {
        val bytes = TestResources.load("simdjson-data/jsonexamples/github_events.json")
        SimdJsonParser().parse(bytes, bytes.size).shouldBeInstanceOf<JsonArray>()
    }

    @Test
    fun `twitter json - count unique users with default profile`() {
        val bytes = TestResources.load("simdjson-data/jsonexamples/twitter.json")
        val root = SimdJsonParser().parse(bytes, bytes.size).shouldBeInstanceOf<JsonObject>()

        val statuses = root["statuses"].shouldBeInstanceOf<JsonArray>()
        val defaultUsers = mutableSetOf<String>()
        for (tweet in statuses) {
            val tweetObj = tweet.shouldBeInstanceOf<JsonObject>()
            val user = tweetObj["user"].shouldBeInstanceOf<JsonObject>()
            val defaultProfile = (user["default_profile"] as? JsonBoolean)?.value ?: false
            if (defaultProfile) {
                defaultUsers.add((user["screen_name"] as JsonString).value)
            }
        }
        withClue("unique users with default_profile in twitter.json") {
            defaultUsers.size shouldBe 86
        }
    }
}
