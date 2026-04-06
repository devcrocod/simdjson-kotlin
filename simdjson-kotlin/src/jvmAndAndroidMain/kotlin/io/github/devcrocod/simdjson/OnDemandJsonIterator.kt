package io.github.devcrocod.simdjson

internal class OnDemandJsonIterator(
    internal val indexer: BitIndexes,
    private val padding: Int
) {

    private val stringParser = StringParser()
    private val numberParser = NumberParser()

    private lateinit var buffer: ByteArray
    private var len: Int = 0
    private var depth: Int = 0

    fun init(buffer: ByteArray, len: Int) {
        if (indexer.isEnd()) {
            throw JsonParsingException("No structural element found.")
        }
        this.buffer = buffer
        this.len = len
        this.depth = 1
    }

    fun getDepth(): Int = depth

    fun setDepth(d: Int) {
        depth = d
    }

    // region Skip

    fun skipChild() {
        skipChild(depth - 1)
    }

    fun skipChild(parentDepth: Int) {
        if (depth <= parentDepth) {
            return
        }
        var idx = indexer.getAndAdvance()
        var character = buffer[idx]

        when (character) {
            OPEN_BRACKET, OPEN_BRACE, COLON_BYTE, COMMA_BYTE -> { /* continue */
            }

            QUOTE -> {
                if (buffer[indexer.peek()] == COLON_BYTE) {
                    indexer.advance() // skip ':'
                } else {
                    depth--
                    if (depth <= parentDepth) return
                }
            }

            else -> {
                depth--
                if (depth <= parentDepth) return
            }
        }

        while (indexer.hasNext()) {
            idx = indexer.getAndAdvance()
            character = buffer[idx]
            val charInt = character.toInt() and 0xFF
            val delta = if (charInt < SKIP_DEPTH_PER_CHARACTER.size) SKIP_DEPTH_PER_CHARACTER[charInt] else 0
            depth += delta
            if (delta < 0 && depth <= parentDepth) {
                return
            }
        }

        throw JsonParsingException("Not enough close braces.")
    }

    // endregion

    // region Boolean

    fun getRootBoolean(): Boolean {
        val idx = indexer.getAndAdvance()
        val result = when (buffer[idx]) {
            TRUE_BYTE -> visitRootTrueAtom(idx)
            FALSE_BYTE -> visitRootFalseAtom(idx)
            else -> throw JsonParsingException("Unrecognized boolean value. Expected: 'true' or 'false'.")
        }
        assertNoMoreJsonValues()
        depth--
        return result
    }

    fun getBoolean(): Boolean {
        val idx = indexer.getAndAdvance()
        val result = when (buffer[idx]) {
            TRUE_BYTE -> visitTrueAtom(idx)
            FALSE_BYTE -> visitFalseAtom(idx)
            else -> throw JsonParsingException("Unrecognized boolean value. Expected: 'true' or 'false'.")
        }
        depth--
        return result
    }

    private fun visitRootTrueAtom(idx: Int): Boolean {
        val valid =
            idx + 4 <= len && isTrue(idx) && (idx + 4 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'true'.")
        }
        return true
    }

    private fun visitRootFalseAtom(idx: Int): Boolean {
        val valid =
            idx + 5 <= len && isFalse(idx) && (idx + 5 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 5]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'false'.")
        }
        return false
    }

    private fun visitTrueAtom(idx: Int): Boolean {
        val valid = isTrue(idx) && CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4])
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'true'.")
        }
        return true
    }

    private fun visitFalseAtom(idx: Int): Boolean {
        val valid = isFalse(idx) && CharacterUtils.isStructuralOrWhitespace(buffer[idx + 5])
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'false'.")
        }
        return false
    }

    private fun isTrue(idx: Int): Boolean =
        buffer[idx] == TRUE_BYTE &&
            buffer[idx + 1] == 'r'.code.toByte() &&
            buffer[idx + 2] == 'u'.code.toByte() &&
            buffer[idx + 3] == 'e'.code.toByte()

    private fun isFalse(idx: Int): Boolean =
        buffer[idx] == FALSE_BYTE &&
            buffer[idx + 1] == 'a'.code.toByte() &&
            buffer[idx + 2] == 'l'.code.toByte() &&
            buffer[idx + 3] == 's'.code.toByte() &&
            buffer[idx + 4] == 'e'.code.toByte()

    fun getNullableBoolean(): Boolean? {
        val idx = indexer.getAndAdvance()
        val result = when (buffer[idx]) {
            TRUE_BYTE -> visitTrueAtom(idx)
            FALSE_BYTE -> visitFalseAtom(idx)
            NULL_BYTE -> {
                visitNullAtom(idx)
                null
            }

            else -> throw JsonParsingException("Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
        }
        depth--
        return result
    }

    fun getRootNullableBoolean(): Boolean? {
        val idx = indexer.getAndAdvance()
        val result = when (buffer[idx]) {
            TRUE_BYTE -> visitRootTrueAtom(idx)
            FALSE_BYTE -> visitRootFalseAtom(idx)
            NULL_BYTE -> {
                visitRootNullAtom(idx)
                null
            }

            else -> throw JsonParsingException("Unrecognized boolean value. Expected: 'true', 'false' or 'null'.")
        }
        assertNoMoreJsonValues()
        depth--
        return result
    }

    // endregion

    // region Null

    fun isRootNull(): Boolean {
        val idx = indexer.peek()
        if (buffer[idx] != NULL_BYTE) return false
        val valid =
            idx + 4 <= len && isNullAtom(idx) && (idx + 4 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'null'.")
        }
        indexer.advance()
        assertNoMoreJsonValues()
        depth--
        return true
    }

    fun isNull(): Boolean {
        val idx = indexer.peek()
        if (buffer[idx] != NULL_BYTE) return false
        visitNullAtom(idx)
        indexer.advance()
        depth--
        return true
    }

    private fun visitRootNullAtom(idx: Int) {
        val valid =
            idx + 4 <= len && isNullAtom(idx) && (idx + 4 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'null'.")
        }
    }

    private fun visitNullAtom(idx: Int) {
        if (!isNullAtom(idx)) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'null'.")
        }
    }

    private fun isNullAtom(idx: Int): Boolean =
        buffer[idx] == NULL_BYTE &&
            buffer[idx + 1] == 'u'.code.toByte() &&
            buffer[idx + 2] == 'l'.code.toByte() &&
            buffer[idx + 3] == 'l'.code.toByte()

    // endregion

    // region Numbers

    fun getRootLong(): Long {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseLong(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getLong(): Long {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseLong(buffer, len, idx)
    }

    fun getRootULong(): ULong {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseULong(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getULong(): ULong {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseULong(buffer, len, idx)
    }

    fun getRootDouble(): Double {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseDouble(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getDouble(): Double {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseDouble(buffer, len, idx)
    }

    private fun padRootNumber(idx: Int): ByteArray {
        val remainingLen = len - idx
        val copy = ByteArray(remainingLen + padding)
        buffer.copyInto(copy, 0, idx, idx + remainingLen)
        copy.fill(SPACE, remainingLen, remainingLen + padding)
        return copy
    }

    // Byte

    fun getRootByte(): Byte {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseByte(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getRootNullableByte(): Byte? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            assertNoMoreJsonValues()
            return null
        }
        val copy = padRootNumber(idx)
        val value = numberParser.parseByte(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getByte(): Byte {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseByte(buffer, len, idx)
    }

    fun getNullableByte(): Byte? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            return null
        }
        return numberParser.parseByte(buffer, len, idx)
    }

    // Short

    fun getRootShort(): Short {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseShort(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getRootNullableShort(): Short? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            assertNoMoreJsonValues()
            return null
        }
        val copy = padRootNumber(idx)
        val value = numberParser.parseShort(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getShort(): Short {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseShort(buffer, len, idx)
    }

    fun getNullableShort(): Short? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            return null
        }
        return numberParser.parseShort(buffer, len, idx)
    }

    // Int

    fun getRootInt(): Int {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseInt(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getRootNullableInt(): Int? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            assertNoMoreJsonValues()
            return null
        }
        val copy = padRootNumber(idx)
        val value = numberParser.parseInt(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getInt(): Int {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseInt(buffer, len, idx)
    }

    fun getNullableInt(): Int? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            return null
        }
        return numberParser.parseInt(buffer, len, idx)
    }

    // Nullable Long

    fun getNullableLong(): Long? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            return null
        }
        return numberParser.parseLong(buffer, len, idx)
    }

    fun getRootNullableLong(): Long? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            assertNoMoreJsonValues()
            return null
        }
        val copy = padRootNumber(idx)
        val value = numberParser.parseLong(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    // Float

    fun getRootFloat(): Float {
        depth--
        val idx = indexer.getAndAdvance()
        val copy = padRootNumber(idx)
        val value = numberParser.parseFloat(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getRootNullableFloat(): Float? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            assertNoMoreJsonValues()
            return null
        }
        val copy = padRootNumber(idx)
        val value = numberParser.parseFloat(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    fun getFloat(): Float {
        depth--
        val idx = indexer.getAndAdvance()
        return numberParser.parseFloat(buffer, len, idx)
    }

    fun getNullableFloat(): Float? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            return null
        }
        return numberParser.parseFloat(buffer, len, idx)
    }

    // Nullable Double

    fun getNullableDouble(): Double? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            return null
        }
        return numberParser.parseDouble(buffer, len, idx)
    }

    fun getRootNullableDouble(): Double? {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            assertNoMoreJsonValues()
            return null
        }
        val copy = padRootNumber(idx)
        val value = numberParser.parseDouble(copy, len - idx, 0)
        assertNoMoreJsonValues()
        return value
    }

    // Char

    fun getChar(): Char {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == QUOTE) {
            return stringParser.parseChar(buffer, idx)
        }
        throw JsonParsingException("Invalid value starting at $idx. Expected string.")
    }

    fun getNullableChar(): Char? {
        depth--
        val idx = indexer.getAndAdvance()
        return when (buffer[idx]) {
            QUOTE -> stringParser.parseChar(buffer, idx)
            NULL_BYTE -> {
                visitNullAtom(idx)
                null
            }

            else -> throw JsonParsingException("Invalid value starting at $idx. Expected either string or 'null'.")
        }
    }

    fun getRootChar(): Char {
        depth--
        val idx = indexer.getAndAdvance()
        if (buffer[idx] == QUOTE) {
            val character = stringParser.parseChar(buffer, idx)
            assertNoMoreJsonValues()
            return character
        }
        throw JsonParsingException("Invalid value starting at $idx. Expected string.")
    }

    fun getRootNullableChar(): Char? {
        depth--
        val idx = indexer.getAndAdvance()
        return when (buffer[idx]) {
            QUOTE -> {
                val character = stringParser.parseChar(buffer, idx)
                assertNoMoreJsonValues()
                character
            }

            NULL_BYTE -> {
                visitRootNullAtom(idx)
                assertNoMoreJsonValues()
                null
            }

            else -> throw JsonParsingException("Invalid value starting at $idx. Expected either string or 'null'.")
        }
    }

    // endregion

    // region Strings

    fun getRootString(stringBuffer: ByteArray): Int {
        depth--
        val idx = indexer.getAndAdvance()
        val result = when (buffer[idx]) {
            QUOTE -> stringParser.parseString(buffer, idx, stringBuffer)
            NULL_BYTE -> {
                visitRootNullAtom(idx)
                -1
            }

            else -> throw JsonParsingException("Invalid value starting at $idx. Expected either string or 'null'.")
        }
        assertNoMoreJsonValues()
        return result
    }

    fun getString(stringBuffer: ByteArray): Int {
        depth--
        val idx = indexer.getAndAdvance()
        return when (buffer[idx]) {
            QUOTE -> stringParser.parseString(buffer, idx, stringBuffer)
            NULL_BYTE -> {
                visitNullAtom(idx)
                -1
            }

            else -> throw JsonParsingException("Invalid value starting at $idx. Expected either string or 'null'.")
        }
    }

    // endregion

    // region Array iteration

    fun startIteratingArray(): IteratorResult {
        var idx = indexer.peek()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            indexer.advance()
            depth--
            return IteratorResult.NULL
        }
        if (buffer[idx] != OPEN_BRACKET) {
            throw unexpectedCharException(idx, '[')
        }
        idx = indexer.advanceAndGet()
        if (buffer[idx] == CLOSE_BRACKET) {
            indexer.advance()
            depth--
            return IteratorResult.EMPTY
        }
        depth++
        return IteratorResult.NOT_EMPTY
    }

    fun startIteratingRootArray(): IteratorResult {
        var idx = indexer.peek()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            indexer.advance()
            depth--
            return IteratorResult.NULL
        }
        if (buffer[idx] != OPEN_BRACKET) {
            throw unexpectedCharException(idx, '[')
        }
        if (buffer[indexer.getLast()] != CLOSE_BRACKET) {
            throw JsonParsingException("Unclosed array. Missing ']' for starting '['.")
        }
        idx = indexer.advanceAndGet()
        if (buffer[idx] == CLOSE_BRACKET) {
            indexer.advance()
            depth--
            assertNoMoreJsonValues()
            return IteratorResult.EMPTY
        }
        depth++
        return IteratorResult.NOT_EMPTY
    }

    fun nextArrayElement(): Boolean {
        val idx = indexer.getAndAdvance()
        return when (buffer[idx]) {
            CLOSE_BRACKET -> {
                depth--
                false
            }

            COMMA_BYTE -> {
                depth++
                true
            }

            else -> throw JsonParsingException("Missing comma between array values")
        }
    }

    // endregion

    // region Object iteration

    fun startIteratingObject(): IteratorResult {
        var idx = indexer.peek()
        if (buffer[idx] == NULL_BYTE) {
            visitNullAtom(idx)
            indexer.advance()
            depth--
            return IteratorResult.NULL
        }
        if (buffer[idx] != OPEN_BRACE) {
            throw unexpectedCharException(idx, '{')
        }
        idx = indexer.advanceAndGet()
        if (buffer[idx] == CLOSE_BRACE) {
            indexer.advance()
            depth--
            return IteratorResult.EMPTY
        }
        return IteratorResult.NOT_EMPTY
    }

    fun startIteratingRootObject(): IteratorResult {
        var idx = indexer.peek()
        if (buffer[idx] == NULL_BYTE) {
            visitRootNullAtom(idx)
            indexer.advance()
            depth--
            return IteratorResult.NULL
        }
        if (buffer[idx] != OPEN_BRACE) {
            throw unexpectedCharException(idx, '{')
        }
        if (buffer[indexer.getLast()] != CLOSE_BRACE) {
            throw JsonParsingException("Unclosed object. Missing '}' for starting '{'.")
        }
        idx = indexer.advanceAndGet()
        if (buffer[idx] == CLOSE_BRACE) {
            indexer.advance()
            depth--
            assertNoMoreJsonValues()
            return IteratorResult.EMPTY
        }
        return IteratorResult.NOT_EMPTY
    }

    fun nextObjectField(): Boolean {
        val idx = indexer.getAndAdvance()
        val character = buffer[idx]
        return when (character) {
            CLOSE_BRACE -> {
                depth--
                false
            }

            COMMA_BYTE -> true
            else -> throw unexpectedCharException(idx, ',')
        }
    }

    fun moveToFieldValue() {
        val idx = indexer.getAndAdvance()
        if (buffer[idx] != COLON_BYTE) {
            throw unexpectedCharException(idx, ':')
        }
        depth++
    }

    fun getFieldName(stringBuffer: ByteArray): Int {
        val idx = indexer.getAndAdvance()
        if (buffer[idx] != QUOTE) {
            throw unexpectedCharException(idx, '"')
        }
        return stringParser.parseString(buffer, idx, stringBuffer)
    }

    // endregion

    // region Type detection

    fun peekType(): JsonType {
        val idx = indexer.peek()
        val b = buffer[idx]
        return when {
            b == OPEN_BRACE -> JsonType.OBJECT
            b == OPEN_BRACKET -> JsonType.ARRAY
            b == QUOTE -> JsonType.STRING
            b == TRUE_BYTE || b == FALSE_BYTE -> JsonType.BOOLEAN
            b == NULL_BYTE -> JsonType.NULL
            b == MINUS_BYTE || (b in ZERO_BYTE..NINE_BYTE) -> JsonType.NUMBER
            else -> throw JsonParsingException("Unrecognized JSON value at index $idx.")
        }
    }

    fun peekNumberIsFloat(): Boolean {
        val idx = indexer.peek()
        var i = idx
        if (buffer[i] == MINUS_BYTE) i++
        while (i < len && !CharacterUtils.isStructuralOrWhitespace(buffer[i])) {
            val b = buffer[i]
            if (b == DOT_BYTE || b == LOWER_E_BYTE || b == UPPER_E_BYTE) {
                return true
            }
            i++
        }
        return false
    }

    /**
     * Peeks at the current integer number to determine if it fits in Long range.
     * Must only be called when [peekNumberIsFloat] returns false.
     */
    fun peekNumberIsInLongRange(): Boolean {
        val idx = indexer.peek()
        val negative = buffer[idx] == MINUS_BYTE
        var i = if (negative) idx + 1 else idx
        val digitsStart = i
        while (i < len && !CharacterUtils.isStructuralOrWhitespace(buffer[i])) i++
        val digitCount = i - digitsStart
        if (digitCount < 19) return true
        if (digitCount > 19) return false
        // Exactly 19 digits: check via parseLong logic
        val firstDigit = buffer[digitsStart]
        // Long.MAX_VALUE = 9223372036854775807 (19 digits, starts with '9')
        // Long.MIN_VALUE = -9223372036854775808 (19 digits abs, starts with '9')
        // If first digit < '9', it fits
        if (firstDigit < '9'.code.toByte()) return true
        // Parse the digits to check overflow
        var digits = 0L
        for (j in digitsStart until i) {
            val d = (buffer[j] - '0'.code.toByte()).toLong()
            digits = 10L * digits + d
        }
        // isOutOfLongRange logic: digits overflowed to negative means it might be Long.MIN_VALUE case
        if (negative && digits == Long.MIN_VALUE) return true
        return digits >= 0
    }

    // endregion

    // region Assertions

    fun assertNoMoreJsonValues() {
        if (indexer.hasNext()) {
            throw JsonParsingException("More than one JSON value at the root of the document, or extra characters at the end of the JSON!")
        }
    }

    private fun unexpectedCharException(idx: Int, expected: Char): JsonParsingException {
        return if (indexer.isPastEnd()) {
            JsonParsingException("Expected '$expected' but reached end of buffer.")
        } else {
            JsonParsingException("Expected '$expected' but got: '${buffer[idx].toInt().toChar()}'.")
        }
    }

    // endregion

    enum class IteratorResult {
        EMPTY, NULL, NOT_EMPTY
    }

    companion object {
        private const val SPACE: Byte = 0x20

        private val SKIP_DEPTH_PER_CHARACTER = IntArray(127).apply {
            this['['.code] = 1
            this['{'.code] = 1
            this[']'.code] = -1
            this['}'.code] = -1
        }

        private val OPEN_BRACE = '{'.code.toByte()
        private val CLOSE_BRACE = '}'.code.toByte()
        private val OPEN_BRACKET = '['.code.toByte()
        private val CLOSE_BRACKET = ']'.code.toByte()
        private val QUOTE = '"'.code.toByte()
        private val COLON_BYTE = ':'.code.toByte()
        private val COMMA_BYTE = ','.code.toByte()
        private val TRUE_BYTE = 't'.code.toByte()
        private val FALSE_BYTE = 'f'.code.toByte()
        private val NULL_BYTE = 'n'.code.toByte()
        private val MINUS_BYTE = '-'.code.toByte()
        private val ZERO_BYTE = '0'.code.toByte()
        private val NINE_BYTE = '9'.code.toByte()
        private val DOT_BYTE = '.'.code.toByte()
        private val LOWER_E_BYTE = 'e'.code.toByte()
        private val UPPER_E_BYTE = 'E'.code.toByte()
    }
}
