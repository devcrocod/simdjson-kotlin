package io.github.devcrocod.simdjson

/**
 * Base exception for all simdjson-kotlin errors.
 */
sealed class SimdJsonException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Invalid JSON input.
 */
class JsonParsingException : SimdJsonException {
    /** Byte offset in the input where the error was detected. */
    val offset: Long

    constructor(message: String) : super(message, null) {
        this.offset = -1
    }

    constructor(message: String, offset: Long, cause: Throwable? = null) : super(message, cause) {
        this.offset = offset
    }
}

/**
 * Type mismatch when accessing a value.
 * E.g., calling getString() on a number.
 */
class JsonTypeException(
    message: String,
    /** Expected type. */
    val expected: JsonType,
    /** Actual type. */
    val actual: JsonType
) : SimdJsonException(message)

/**
 * Accessing an already-consumed On Demand value or
 * out-of-order iteration.
 */
class JsonIterationException(
    message: String
) : SimdJsonException(message)

/**
 * UTF-8 validation error.
 */
class JsonEncodingException(
    message: String
) : SimdJsonException(message)

/**
 * Error loading the native simdjson library.
 */
class NativeLibraryException(
    message: String,
    cause: Throwable? = null
) : SimdJsonException(message, cause)
