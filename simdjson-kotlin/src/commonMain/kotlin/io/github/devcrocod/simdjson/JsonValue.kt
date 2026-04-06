package io.github.devcrocod.simdjson

/**
 * Represents a parsed JSON value.
 * Immutable and safe to share across threads after parsing.
 */
sealed interface JsonValue

/**
 * JSON object: {"key": value, ...}
 */
class JsonObject private constructor(
    private val tape: Tape?,
    private val tapeIdx: Int,
    private val stringBuffer: ByteArray?,
    private val _entries: List<Pair<String, JsonValue>>?
) : JsonValue, Iterable<Pair<String, JsonValue>> {

    internal constructor(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray)
        : this(tape, tapeIdx, stringBuffer, null)

    internal constructor(entries: List<Pair<String, JsonValue>>)
        : this(null, 0, null, entries)

    /** Number of key-value pairs. */
    val size: Int
        get() = _entries?.size ?: tape!!.getScopeCount(tapeIdx)

    /** Get value by key, or null if not found. */
    operator fun get(key: String): JsonValue? {
        if (_entries != null) {
            return _entries.firstOrNull { it.first == key }?.second
        }
        val keyBytes = key.encodeToByteArray()
        var idx = tapeIdx + 1
        val endIdx = tape!!.getMatchingBraceIndex(tapeIdx) - 1
        while (idx < endIdx) {
            val sbIdx = tape.getValue(idx).toInt()
            val len = IntegerUtils.toInt(stringBuffer!!, sbIdx)
            val valIdx = tape.computeNextIndex(idx)
            val nextKeyIdx = tape.computeNextIndex(valIdx)
            if (len == keyBytes.size) {
                val from = sbIdx + Int.SIZE_BYTES
                if (regionEquals(keyBytes, 0, stringBuffer, from, len)) {
                    return materializeTapeValue(tape, valIdx, stringBuffer)
                }
            }
            idx = nextKeyIdx
        }
        return null
    }

    /** All keys in insertion order. */
    fun keys(): Set<String> {
        if (_entries != null) {
            return _entries.mapTo(LinkedHashSet()) { it.first }
        }
        val result = LinkedHashSet<String>()
        var idx = tapeIdx + 1
        val endIdx = tape!!.getMatchingBraceIndex(tapeIdx) - 1
        while (idx < endIdx) {
            result.add(readStringFromTape(tape, idx, stringBuffer!!))
            val valIdx = tape.computeNextIndex(idx)
            idx = tape.computeNextIndex(valIdx)
        }
        return result
    }

    /** Iterate over key-value pairs. */
    override fun iterator(): Iterator<Pair<String, JsonValue>> {
        if (_entries != null) return _entries.iterator()
        return TapeObjectIterator()
    }

    /** Check if key exists. */
    operator fun contains(key: String): Boolean = get(key) != null

    private inner class TapeObjectIterator : Iterator<Pair<String, JsonValue>> {
        private var idx = tapeIdx + 1
        private val endIdx = tape!!.getMatchingBraceIndex(tapeIdx) - 1

        override fun hasNext(): Boolean = idx < endIdx

        override fun next(): Pair<String, JsonValue> {
            if (!hasNext()) throw NoSuchElementException("No more fields")
            val key = readStringFromTape(tape!!, idx, stringBuffer!!)
            idx = tape.computeNextIndex(idx)
            val value = materializeTapeValue(tape, idx, stringBuffer)
            idx = tape.computeNextIndex(idx)
            return key to value
        }
    }
}

/**
 * JSON array: [value, ...]
 */
class JsonArray private constructor(
    private val tape: Tape?,
    private val tapeIdx: Int,
    private val stringBuffer: ByteArray?,
    private val _elements: List<JsonValue>?
) : JsonValue, Iterable<JsonValue> {

    internal constructor(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray)
        : this(tape, tapeIdx, stringBuffer, null)

    internal constructor(elements: List<JsonValue>)
        : this(null, 0, null, elements)

    /** Number of elements. */
    val size: Int
        get() = _elements?.size ?: tape!!.getScopeCount(tapeIdx)

    /** Get element by index. Throws [IndexOutOfBoundsException]. */
    operator fun get(index: Int): JsonValue {
        if (_elements != null) {
            return _elements[index]
        }
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index $index, size $size")
        var idx = tapeIdx + 1
        repeat(index) { idx = tape!!.computeNextIndex(idx) }
        return materializeTapeValue(tape!!, idx, stringBuffer!!)
    }

    /** Iterate over elements. */
    override fun iterator(): Iterator<JsonValue> {
        if (_elements != null) return _elements.iterator()
        return TapeArrayIterator()
    }

    private inner class TapeArrayIterator : Iterator<JsonValue> {
        private var idx = tapeIdx + 1
        private val endIdx = tape!!.getMatchingBraceIndex(tapeIdx) - 1

        override fun hasNext(): Boolean = idx < endIdx

        override fun next(): JsonValue {
            if (!hasNext()) throw NoSuchElementException("No more elements")
            val value = materializeTapeValue(tape!!, idx, stringBuffer!!)
            idx = tape.computeNextIndex(idx)
            return value
        }
    }
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

private fun regionEquals(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int, length: Int): Boolean {
    for (i in 0 until length) {
        if (a[aOffset + i] != b[bOffset + i]) return false
    }
    return true
}
