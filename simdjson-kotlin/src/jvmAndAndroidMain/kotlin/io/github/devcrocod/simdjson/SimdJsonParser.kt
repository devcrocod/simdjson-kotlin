package io.github.devcrocod.simdjson

actual class SimdJsonParser actual constructor(
    capacity: Int,
    maxDepth: Int
) : AutoCloseable {

    internal val backend: ParserBackend = createParserBackend(capacity, maxDepth)

    actual fun parse(data: ByteArray, length: Int): JsonValue =
        backend.parse(data, length)

    actual fun parse(json: String): JsonValue {
        val bytes = json.encodeToByteArray()
        return parse(bytes, bytes.size)
    }

    actual fun iterate(data: ByteArray, length: Int): JsonDocument =
        backend.iterate(data, length)

    actual fun iterate(json: String): JsonDocument {
        val bytes = json.encodeToByteArray()
        return iterate(bytes, bytes.size)
    }

    actual override fun close() {
        backend.close()
    }

    actual companion object {
        actual val DEFAULT_CAPACITY: Int = 34 * 1024 * 1024
        actual val DEFAULT_MAX_DEPTH: Int = 1024
        actual val SIMDJSON_PADDING: Int = 64
    }
}
