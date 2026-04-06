package io.github.devcrocod.simdjson

/**
 * High-performance SIMD-accelerated JSON parser.
 *
 * NOT thread-safe. Reuse a single instance per thread
 * to amortize internal buffer allocations.
 *
 * On Native targets, holds native memory — must be closed
 * after use via [close] or [use].
 *
 * @param capacity maximum input size in bytes (default 34 MiB)
 * @param maxDepth maximum nesting depth (default 1024)
 */
expect class SimdJsonParser(
    capacity: Int = DEFAULT_CAPACITY,
    maxDepth: Int = DEFAULT_MAX_DEPTH
) : AutoCloseable {

    // --- DOM API ---

    /**
     * Parse JSON bytes into a full DOM tree.
     *
     * @param data JSON input as a byte array (UTF-8 encoded)
     * @param length number of bytes to parse (defaults to full array)
     * @return parsed [JsonValue] representing the root element
     * @throws JsonParsingException if the input is not valid JSON
     * @throws JsonEncodingException if the input is not valid UTF-8
     */
    fun parse(data: ByteArray, length: Int = data.size): JsonValue

    /**
     * Parse a JSON string into a full DOM tree.
     *
     * @param json JSON input as a string
     * @return parsed [JsonValue] representing the root element
     * @throws JsonParsingException if the input is not valid JSON
     */
    fun parse(json: String): JsonValue

    // --- On Demand API ---

    /**
     * Begin lazy forward-only iteration over JSON bytes.
     *
     * The returned [JsonDocument] is only valid until the next
     * [parse] or [iterate] call on this parser.
     *
     * @param data JSON input as a byte array (UTF-8 encoded)
     * @param length number of bytes to parse (defaults to full array)
     * @return a [JsonDocument] for on-demand traversal
     * @throws JsonParsingException if the input is not valid JSON
     * @throws JsonEncodingException if the input is not valid UTF-8
     */
    fun iterate(data: ByteArray, length: Int = data.size): JsonDocument

    /**
     * Begin lazy forward-only iteration over a JSON string.
     *
     * The returned [JsonDocument] is only valid until the next
     * [parse] or [iterate] call on this parser.
     *
     * @param json JSON input as a string
     * @return a [JsonDocument] for on-demand traversal
     * @throws JsonParsingException if the input is not valid JSON
     */
    fun iterate(json: String): JsonDocument

    // --- Lifecycle ---

    /**
     * Release resources held by this parser.
     *
     * On JVM this is a no-op (GC handles cleanup).
     * On Native targets this frees the underlying C++ parser memory.
     */
    override fun close()

    companion object {
        /** Default maximum input capacity: 34 MiB. */
        val DEFAULT_CAPACITY: Int

        /** Default maximum JSON nesting depth. */
        val DEFAULT_MAX_DEPTH: Int

        /** Number of padding bytes required after JSON input for SIMD processing. */
        val SIMDJSON_PADDING: Int
    }
}
