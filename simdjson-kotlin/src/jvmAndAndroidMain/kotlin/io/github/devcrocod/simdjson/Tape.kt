package io.github.devcrocod.simdjson

internal class Tape(capacity: Int) {

    private val tape = LongArray(capacity)
    private var tapeIdx = 0

    fun append(value: Long, type: Char) {
        tape[tapeIdx] = value or (type.code.toLong() shl 56)
        tapeIdx++
    }

    fun appendInt64(value: Long) {
        append(0, INT64)
        tape[tapeIdx] = value
        tapeIdx++
    }

    fun appendUInt64(value: Long) {
        append(0, UINT64)
        tape[tapeIdx] = value
        tapeIdx++
    }

    fun appendDouble(value: Double) {
        append(0, DOUBLE)
        tape[tapeIdx] = value.toRawBits()
        tapeIdx++
    }

    fun write(idx: Int, value: Long, type: Char) {
        tape[idx] = value or (type.code.toLong() shl 56)
    }

    fun skip() {
        tapeIdx++
    }

    fun reset() {
        tapeIdx = 0
    }

    fun getCurrentIdx(): Int = tapeIdx

    fun getType(idx: Int): Char = (tape[idx] ushr 56).toInt().toChar()

    fun getValue(idx: Int): Long = tape[idx] and JSON_VALUE_MASK

    fun getInt64Value(idx: Int): Long = tape[idx + 1]

    fun getDouble(idx: Int): Double = Double.fromBits(getInt64Value(idx))

    fun getMatchingBraceIndex(idx: Int): Int = tape[idx].toInt()

    fun getScopeCount(idx: Int): Int = ((tape[idx] ushr 32) and JSON_COUNT_MASK.toLong()).toInt()

    fun computeNextIndex(idx: Int): Int = when (getType(idx)) {
        START_ARRAY, START_OBJECT -> getMatchingBraceIndex(idx)
        INT64, UINT64, DOUBLE -> idx + 2
        else -> idx + 1
    }

    companion object {
        const val ROOT: Char = 'r'
        const val START_ARRAY: Char = '['
        const val START_OBJECT: Char = '{'
        const val END_ARRAY: Char = ']'
        const val END_OBJECT: Char = '}'
        const val STRING: Char = '"'
        const val INT64: Char = 'l'
        const val UINT64: Char = 'u'
        const val DOUBLE: Char = 'd'
        const val TRUE_VALUE: Char = 't'
        const val FALSE_VALUE: Char = 'f'
        const val NULL_VALUE: Char = 'n'

        private const val JSON_VALUE_MASK: Long = 0x00FFFFFFFFFFFFFFL
        private const val JSON_COUNT_MASK: Int = 0xFFFFFF
    }
}
