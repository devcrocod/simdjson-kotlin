package io.github.devcrocod.simdjson.serialization

data class SimdJsonConfiguration(
    val ignoreUnknownKeys: Boolean = false,
    val isLenient: Boolean = false,
    val coerceInputValues: Boolean = false
)
