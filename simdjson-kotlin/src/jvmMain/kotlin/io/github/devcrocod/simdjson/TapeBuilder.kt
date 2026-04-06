package io.github.devcrocod.simdjson

internal class TapeBuilder(capacity: Int, depth: Int, private val padding: Int, private val stringBuffer: ByteArray) {

    private val tape = Tape(capacity)
    private val openContainers = Array(depth) { OpenContainer() }
    private val numberParser = NumberParser()
    private val stringParser = StringParser()
    private var stringBufferIdx = 0

    fun visitDocumentStart() {
        startContainer(0)
    }

    fun visitDocumentEnd() {
        tape.append(0, Tape.ROOT)
        tape.write(0, tape.getCurrentIdx().toLong(), Tape.ROOT)
    }

    fun visitEmptyObject() {
        emptyContainer(Tape.START_OBJECT, Tape.END_OBJECT)
    }

    fun visitEmptyArray() {
        emptyContainer(Tape.START_ARRAY, Tape.END_ARRAY)
    }

    fun visitRootPrimitive(buffer: ByteArray, idx: Int, len: Int) {
        when (buffer[idx]) {
            '"'.code.toByte() -> visitString(buffer, idx)
            't'.code.toByte() -> visitRootTrueAtom(buffer, idx, len)
            'f'.code.toByte() -> visitRootFalseAtom(buffer, idx, len)
            'n'.code.toByte() -> visitRootNullAtom(buffer, idx, len)
            '-'.code.toByte(),
            '0'.code.toByte(), '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(),
            '5'.code.toByte(), '6'.code.toByte(), '7'.code.toByte(), '8'.code.toByte(), '9'.code.toByte() ->
                visitRootNumber(buffer, idx, len)

            else -> throw JsonParsingException("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.")
        }
    }

    fun visitPrimitive(buffer: ByteArray, idx: Int) {
        when (buffer[idx]) {
            '"'.code.toByte() -> visitString(buffer, idx)
            't'.code.toByte() -> visitTrueAtom(buffer, idx)
            'f'.code.toByte() -> visitFalseAtom(buffer, idx)
            'n'.code.toByte() -> visitNullAtom(buffer, idx)
            '-'.code.toByte(),
            '0'.code.toByte(), '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(),
            '5'.code.toByte(), '6'.code.toByte(), '7'.code.toByte(), '8'.code.toByte(), '9'.code.toByte() ->
                visitNumber(buffer, idx)

            else -> throw JsonParsingException("Unrecognized primitive. Expected: string, number, 'true', 'false' or 'null'.")
        }
    }

    fun visitObjectStart(depth: Int) {
        startContainer(depth)
    }

    fun incrementCount(depth: Int) {
        openContainers[depth].count++
    }

    fun visitObjectEnd(depth: Int) {
        endContainer(Tape.START_OBJECT, Tape.END_OBJECT, depth)
    }

    fun visitArrayStart(depth: Int) {
        startContainer(depth)
    }

    fun visitArrayEnd(depth: Int) {
        endContainer(Tape.START_ARRAY, Tape.END_ARRAY, depth)
    }

    private fun visitTrueAtom(buffer: ByteArray, idx: Int) {
        val valid = isTrue(buffer, idx) && CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4])
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'true'.")
        }
        tape.append(0, Tape.TRUE_VALUE)
    }

    private fun visitRootTrueAtom(buffer: ByteArray, idx: Int, len: Int) {
        val valid = idx + 4 <= len && isTrue(
            buffer,
            idx
        ) && (idx + 4 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'true'.")
        }
        tape.append(0, Tape.TRUE_VALUE)
    }

    private fun isTrue(buffer: ByteArray, idx: Int): Boolean {
        return buffer[idx] == 't'.code.toByte()
            && buffer[idx + 1] == 'r'.code.toByte()
            && buffer[idx + 2] == 'u'.code.toByte()
            && buffer[idx + 3] == 'e'.code.toByte()
    }

    private fun visitFalseAtom(buffer: ByteArray, idx: Int) {
        val valid = isFalse(buffer, idx) && CharacterUtils.isStructuralOrWhitespace(buffer[idx + 5])
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'false'.")
        }
        tape.append(0, Tape.FALSE_VALUE)
    }

    private fun visitRootFalseAtom(buffer: ByteArray, idx: Int, len: Int) {
        val valid = idx + 5 <= len && isFalse(
            buffer,
            idx
        ) && (idx + 5 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 5]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'false'.")
        }
        tape.append(0, Tape.FALSE_VALUE)
    }

    private fun isFalse(buffer: ByteArray, idx: Int): Boolean {
        return buffer[idx] == 'f'.code.toByte()
            && buffer[idx + 1] == 'a'.code.toByte()
            && buffer[idx + 2] == 'l'.code.toByte()
            && buffer[idx + 3] == 's'.code.toByte()
            && buffer[idx + 4] == 'e'.code.toByte()
    }

    private fun visitNullAtom(buffer: ByteArray, idx: Int) {
        val valid = isNull(buffer, idx) && CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4])
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'null'.")
        }
        tape.append(0, Tape.NULL_VALUE)
    }

    private fun visitRootNullAtom(buffer: ByteArray, idx: Int, len: Int) {
        val valid = idx + 4 <= len && isNull(
            buffer,
            idx
        ) && (idx + 4 == len || CharacterUtils.isStructuralOrWhitespace(buffer[idx + 4]))
        if (!valid) {
            throw JsonParsingException("Invalid value starting at $idx. Expected 'null'.")
        }
        tape.append(0, Tape.NULL_VALUE)
    }

    private fun isNull(buffer: ByteArray, idx: Int): Boolean {
        return buffer[idx] == 'n'.code.toByte()
            && buffer[idx + 1] == 'u'.code.toByte()
            && buffer[idx + 2] == 'l'.code.toByte()
            && buffer[idx + 3] == 'l'.code.toByte()
    }

    fun visitKey(buffer: ByteArray, idx: Int) {
        visitString(buffer, idx)
    }

    private fun visitString(buffer: ByteArray, idx: Int) {
        tape.append(stringBufferIdx.toLong(), Tape.STRING)
        stringBufferIdx = stringParser.parseString(buffer, idx, stringBuffer, stringBufferIdx)
    }

    private fun visitNumber(buffer: ByteArray, idx: Int) {
        numberParser.parseNumber(buffer, idx, tape)
    }

    private fun visitRootNumber(buffer: ByteArray, idx: Int, len: Int) {
        val remainingLen = len - idx
        val copy = ByteArray(remainingLen + padding)
        buffer.copyInto(copy, destinationOffset = 0, startIndex = idx, endIndex = idx + remainingLen)
        copy.fill(SPACE, fromIndex = remainingLen, toIndex = remainingLen + padding)
        numberParser.parseNumber(copy, 0, tape)
    }

    private fun startContainer(depth: Int) {
        openContainers[depth].tapeIndex = tape.getCurrentIdx()
        openContainers[depth].count = 0
        tape.skip()
    }

    private fun endContainer(start: Char, end: Char, depth: Int) {
        val startTapeIndex = openContainers[depth].tapeIndex
        tape.append(startTapeIndex.toLong(), end)
        val count = minOf(openContainers[depth].count, 0xFFFFFF)
        tape.write(startTapeIndex, tape.getCurrentIdx().toLong() or (count.toLong() shl 32), start)
    }

    private fun emptyContainer(start: Char, end: Char) {
        tape.append((tape.getCurrentIdx() + 2).toLong(), start)
        tape.append(tape.getCurrentIdx().toLong(), end)
    }

    fun createJsonValue(): JsonValue = materializeTapeValue(tape, 1, stringBuffer)

    fun reset() {
        tape.reset()
        stringBufferIdx = 0
    }

    private class OpenContainer {
        var tapeIndex: Int = 0
        var count: Int = 0
    }

    companion object {
        private const val SPACE: Byte = 0x20
    }
}
