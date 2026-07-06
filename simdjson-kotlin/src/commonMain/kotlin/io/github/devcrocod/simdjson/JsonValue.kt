package io.github.devcrocod.simdjson

/**
 * Represents a parsed JSON value.
 * Immutable and safe to share across threads after parsing.
 */
sealed interface JsonValue

/**
 * JSON object: {"key": value, ...}
 */
class JsonObject internal constructor(
    private val entries: List<Pair<String, JsonValue>>
) : JsonValue, Iterable<Pair<String, JsonValue>> {

    /** Number of key-value pairs. */
    val size: Int
        get() = entries.size

    /** Get value by key, or null if not found. */
    operator fun get(key: String): JsonValue? = entries.firstOrNull { it.first == key }?.second

    /** All keys in insertion order. */
    fun keys(): Set<String> = entries.mapTo(LinkedHashSet()) { it.first }

    /** Iterate over key-value pairs. */
    override fun iterator(): Iterator<Pair<String, JsonValue>> = entries.iterator()

    /** Check if key exists. */
    operator fun contains(key: String): Boolean = get(key) != null
}

/**
 * JSON array: [value, ...]
 */
class JsonArray internal constructor(
    private val elements: List<JsonValue>
) : JsonValue, Iterable<JsonValue> {

    /** Number of elements. */
    val size: Int
        get() = elements.size

    /** Get element by index. Throws [IndexOutOfBoundsException]. */
    operator fun get(index: Int): JsonValue = elements[index]

    /** Iterate over elements. */
    override fun iterator(): Iterator<JsonValue> = elements.iterator()
}

/**
 * JSON string: "..."
 * Always a Kotlin String copy (not a view into parser buffer).
 */
@kotlin.jvm.JvmInline
value class JsonString(val value: String) : JsonValue

/**
 * JSON number.
 * Stores the parsed numeric value.
 */
class JsonNumber private constructor(
    private val longVal: Long,
    private val ulongVal: ULong,
    private val doubleVal: Double,
    private val type: NumType
) : JsonValue {

    internal constructor(longValue: Long) : this(longValue, 0u, 0.0, NumType.INT64)
    internal constructor(doubleValue: Double) : this(0L, 0u, doubleValue, NumType.DOUBLE)

    internal companion object {
        fun ofULong(value: ULong) = JsonNumber(0L, value, 0.0, NumType.UINT64)
    }

    private enum class NumType { INT64, UINT64, DOUBLE }

    /** True if the number is an integer (no fractional part, no exponent). */
    val isInteger: Boolean get() = type == NumType.INT64 || type == NumType.UINT64

    /** True if the number fits in Long range. */
    val isLong: Boolean get() = type == NumType.INT64

    /** True if the number is unsigned (positive, may exceed Long.MAX_VALUE). */
    val isUnsigned: Boolean get() = type == NumType.UINT64

    /** Get as Long. Throws if the value exceeds Long range or is a double. */
    fun toLong(): Long = when (type) {
        NumType.INT64 -> longVal
        NumType.UINT64 -> {
            if (ulongVal > Long.MAX_VALUE.toULong())
                throw JsonParsingException("Value $ulongVal exceeds Long range")
            ulongVal.toLong()
        }
        NumType.DOUBLE -> throw JsonTypeException(
            "Cannot convert double to long without precision loss",
            expected = JsonType.NUMBER,
            actual = JsonType.NUMBER
        )
    }

    /** Get as ULong. Throws if the value is negative or a double. */
    fun toULong(): ULong = when (type) {
        NumType.INT64 -> {
            if (longVal < 0) throw JsonParsingException("Cannot represent negative number as ULong")
            longVal.toULong()
        }
        NumType.UINT64 -> ulongVal
        NumType.DOUBLE -> throw JsonTypeException(
            "Cannot convert double to ULong",
            expected = JsonType.NUMBER,
            actual = JsonType.NUMBER
        )
    }

    /** Get as Double. May lose precision for large integers. */
    fun toDouble(): Double = when (type) {
        NumType.INT64 -> longVal.toDouble()
        NumType.UINT64 -> ulongVal.toDouble()
        NumType.DOUBLE -> doubleVal
    }

    /** Get as Int. Throws if the value is out of Int range or is a double. */
    fun toInt(): Int = when (type) {
        NumType.INT64 -> {
            if (longVal < Int.MIN_VALUE || longVal > Int.MAX_VALUE)
                throw JsonParsingException("Value $longVal out of Int range")
            longVal.toInt()
        }
        NumType.UINT64 -> {
            if (ulongVal > Int.MAX_VALUE.toULong())
                throw JsonParsingException("Value $ulongVal out of Int range")
            ulongVal.toInt()
        }
        NumType.DOUBLE -> throw JsonTypeException(
            "Cannot convert double to int without precision loss",
            expected = JsonType.NUMBER,
            actual = JsonType.NUMBER
        )
    }
}

/**
 * JSON boolean: true/false
 */
@kotlin.jvm.JvmInline
value class JsonBoolean(val value: Boolean) : JsonValue

/**
 * JSON null literal.
 */
data object JsonNull : JsonValue
