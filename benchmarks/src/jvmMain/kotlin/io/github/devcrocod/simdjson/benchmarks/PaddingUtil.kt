package io.github.devcrocod.simdjson.benchmarks

import io.github.devcrocod.simdjson.SimdJsonParser

fun padded(src: ByteArray): ByteArray {
    val padded = ByteArray(src.size + SimdJsonParser.SIMDJSON_PADDING)
    src.copyInto(padded)
    return padded
}
