---
title: Parsing twitter.json
description: "The same query solved three ways: DOM, On-Demand, and kotlinx.serialization."
weight: 10
---

A classic simdjson benchmark input is `twitter.json`, a Twitter API response
with a top-level `statuses` array. The task below is the same in every tab:
**print the screen name of every user whose `default_profile` is `true`.**

Each API expresses it differently. Compare the three and pick the one that fits
your access pattern.

{{< code-tabs >}}
{{< code-tab title="DOM" lang="kotlin" >}}
val json: ByteArray = loadTwitterJson()

SimdJsonParser().use { parser ->
    val root = parser.parse(json) as JsonObject
    val statuses = root["statuses"] as JsonArray
    for (tweet in statuses) {
        val user = (tweet as JsonObject)["user"] as JsonObject
        if ((user["default_profile"] as JsonBoolean).value) {
            println((user["screen_name"] as JsonString).value)
        }
    }
}
{{< /code-tab >}}
{{< code-tab title="On-Demand" lang="kotlin" >}}
val json: ByteArray = loadTwitterJson()

SimdJsonParser().use { parser ->
    parser.iterate(json).use { doc ->
        for (tweet in doc.getObject()["statuses"].getArray()) {
            val user = tweet.getObject()["user"].getObject()
            if (user["default_profile"].getBoolean()) {
                println(user["screen_name"].getString())
            }
        }
    }
}
{{< /code-tab >}}
{{< code-tab title="Serialization (JVM)" lang="kotlin" >}}
@Serializable
data class Twitter(val statuses: List<Status>)

@Serializable
data class Status(val user: User)

@Serializable
data class User(val default_profile: Boolean, val screen_name: String)

val json: String = loadTwitterJson()

val simdJson = SimdJson { ignoreUnknownKeys = true }
for (status in simdJson.decodeFromString<Twitter>(json).statuses) {
    if (status.user.default_profile) {
        println(status.user.screen_name)
    }
}
{{< /code-tab >}}
{{< /code-tabs >}}

New to these APIs? Start with the [DOM](../../guides/dom/),
[On-Demand](../../guides/on-demand/), and
[serialization](../../guides/serialization/) guides.
