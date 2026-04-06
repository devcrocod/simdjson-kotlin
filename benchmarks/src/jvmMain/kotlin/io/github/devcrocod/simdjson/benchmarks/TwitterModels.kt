package io.github.devcrocod.simdjson.benchmarks

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- kotlinx.serialization models (SimdJson serialization + kotlinx.serialization-json) ---

@Serializable
data class TwitterData(
    val statuses: List<Tweet>
)

@Serializable
data class Tweet(
    val user: TwitterUser
)

@Serializable
data class TwitterUser(
    @SerialName("default_profile") val defaultProfile: Boolean,
    @SerialName("screen_name") val screenName: String
)

// --- Jackson models ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class JacksonTwitterData(
    val statuses: List<JacksonTweet>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JacksonTweet(
    val user: JacksonTwitterUser
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JacksonTwitterUser(
    val default_profile: Boolean,
    val screen_name: String
)

// --- simdjson-kotlin schema-based parser models ---

data class SimdJsonTwitterData(
    val statuses: List<SimdJsonTweet>
)

data class SimdJsonTweet(
    val user: SimdJsonTwitterUser
)

data class SimdJsonTwitterUser(
    val default_profile: Boolean,
    val screen_name: String
)
