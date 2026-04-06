package io.github.devcrocod.simdjson

internal class BitIndexes(capacity: Int) {

    private val indexes = IntArray(capacity)
    private var writeIdx = 0
    private var readIdx = 0

    fun write(blockIndex: Int, bits: Long) {
        var b = bits
        if (b == 0L) return

        val idx = blockIndex - 64
        val cnt = b.countOneBits()
        for (i in 0 until 8) {
            indexes[i + writeIdx] = idx + b.countTrailingZeroBits()
            b = clearLowestBit(b)
        }

        if (cnt > 8) {
            for (i in 8 until 16) {
                indexes[i + writeIdx] = idx + b.countTrailingZeroBits()
                b = clearLowestBit(b)
            }
            if (cnt > 16) {
                var i = 16
                do {
                    indexes[i + writeIdx] = idx + b.countTrailingZeroBits()
                    b = clearLowestBit(b)
                    i++
                } while (i < cnt)
            }
        }
        writeIdx += cnt
    }

    private fun clearLowestBit(bits: Long): Long = bits and (bits - 1)

    fun advance() {
        readIdx++
    }

    fun getAndAdvance(): Int {
        assert(readIdx <= writeIdx)
        return indexes[readIdx++]
    }

    fun getLast(): Int = indexes[writeIdx - 1]

    fun advanceAndGet(): Int {
        assert(readIdx + 1 <= writeIdx)
        return indexes[++readIdx]
    }

    fun peek(): Int {
        assert(readIdx <= writeIdx)
        return indexes[readIdx]
    }

    fun hasNext(): Boolean = writeIdx > readIdx

    fun isEnd(): Boolean = writeIdx == readIdx

    fun isPastEnd(): Boolean = readIdx > writeIdx

    fun getPosition(): Int = readIdx
    fun setPosition(pos: Int) {
        readIdx = pos
    }

    // If we go past the end of the detected structural indexes, it means we are dealing with an invalid JSON.
    // Thus, we need to stop processing immediately and throw an exception. To avoid checking after every increment
    // of readIdx whether this has happened, we jump to the first structural element. This should produce the
    // desired outcome, i.e., an iterator should detect invalid JSON. To understand how this works, let's first
    // exclude primitive values (numbers, strings, booleans, nulls) from the scope of possible JSON documents. We
    // can do this because, when these values are parsed, the length of the input buffer is verified, ensuring we
    // never go past its end. Therefore, we can focus solely on objects and arrays. Since we always check that if
    // the first character is '{', the last one must be '}', and if the first character is '[', the last one must
    // be ']', we know that if we've reached beyond the buffer without crashing, the input is either '{...}' or '[...]'.
    // Thus, if we jump to the first structural element, we will generate either '{...}{' or '[...]['. Both of these
    // are invalid sequences and will be detected by the iterator, which will then stop processing and throw an
    // exception informing about the invalid JSON.
    fun finish() {
        indexes[writeIdx] = 0
    }

    fun reset() {
        writeIdx = 0
        readIdx = 0
    }

    fun loadFromArray(indices: IntArray, count: Int) {
        indices.copyInto(indexes, 0, 0, count)
        writeIdx = count
        readIdx = 0
        indexes[writeIdx] = 0  // sentinel — see finish() comment
    }
}
