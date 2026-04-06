package io.github.devcrocod.simdjson

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorMask
import jdk.incubator.vector.VectorOperators.EQ
import jdk.incubator.vector.VectorOperators.LSHL
import jdk.incubator.vector.VectorOperators.LSHR
import jdk.incubator.vector.VectorOperators.NE
import jdk.incubator.vector.VectorOperators.UGE
import jdk.incubator.vector.VectorOperators.UGT
import jdk.incubator.vector.VectorShuffle

internal object Utf8Validator {

    // Leading byte not followed by a continuation byte but by another leading or ASCII byte, e.g. 11______ 0_______, 11______ 11______
    private const val TOO_SHORT: Byte = 1

    // ASCII followed by continuation byte e.g. 01111111 10_000000.
    private const val TOO_LONG: Byte = (1 shl 1).toByte()

    // Any 3-byte sequence that could be represented by a shorter sequence (any sequence smaller than 1110_0000 10_100000 10_000000).
    private const val OVERLONG_3BYTE: Byte = (1 shl 2).toByte()

    // Any decoded code point greater than U+10FFFF. e.g. 11110_100 10_010000 10_000000 10_000000.
    private const val TOO_LARGE: Byte = (1 shl 3).toByte()

    // Code points in the range of U+D800 - U+DFFF (inclusive) are the surrogates for UTF-16.
    // These 2048 code points that are reserved for UTF-16 are disallowed in UTF-8, e.g. 1110_1101 10_100000 10_000000.
    private const val SURROGATE: Byte = (1 shl 4).toByte()

    // First valid 2-byte sequence: 110_00010 10_000000. Anything smaller is considered overlong as it fits into a 1-byte sequence.
    private const val OVERLONG_2BYTE: Byte = (1 shl 5).toByte()

    // Similar to TOO_LARGE, but for cases where the continuation byte's high nibble is 1000, e.g. 11110_101 10_000000 10_000000.
    private const val TOO_LARGE_1000: Byte = (1 shl 6).toByte()

    // Any decoded code point below above U+FFFF, e.g. 11110_000 10_000000 10_000000 10_000000.
    private const val OVERLONG_4BYTE: Byte = (1 shl 6).toByte()

    // An example: 10_000000 10_000000.
    private const val TWO_CONTINUATIONS: Byte = -128 // (1 shl 7).toByte()

    private val MAX_2_LEADING_BYTE: Byte = 0b110_11111.toByte()
    private val MAX_3_LEADING_BYTE: Byte = 0b1110_1111.toByte()
    private const val TWO_BYTES_SIZE: Int = Byte.SIZE_BITS * 2
    private const val THREE_BYTES_SIZE: Int = Byte.SIZE_BITS * 3
    private val BYTE_1_HIGH_LOOKUP: ByteVector = createByte1HighLookup()
    private val BYTE_1_LOW_LOOKUP: ByteVector = createByte1LowLookup()
    private val BYTE_2_HIGH_LOOKUP: ByteVector = createByte2HighLookup()
    private val INCOMPLETE_CHECK: ByteVector = createIncompleteCheck()
    private const val LOW_NIBBLE_MASK: Byte = 0b0000_1111
    private val ALL_ASCII_MASK: Byte = 0b1000_0000.toByte()
    private val FOUR_BYTES_FORWARD_SHIFT: VectorShuffle<Int> =
        VectorShuffle.iota(VectorUtils.INT_SPECIES, VectorUtils.INT_SPECIES.elementSize() - 1, 1, true)
    private val STEP_SIZE: Int = VectorUtils.BYTE_SPECIES.vectorByteSize()

    private infix fun Byte.bor(other: Byte): Byte = (this.toInt() or other.toInt()).toByte()

    fun validate(buffer: ByteArray, length: Int) {
        var previousIncomplete = 0L
        var errors = 0L
        var previousFourUtf8Bytes = 0

        val loopBound = VectorUtils.BYTE_SPECIES.loopBound(length)
        var offset = 0
        while (offset < loopBound) {
            val chunk = ByteVector.fromArray(VectorUtils.BYTE_SPECIES, buffer, offset)
            val chunkAsInts = chunk.reinterpretAsInts()
            // ASCII fast path can bypass the checks that are only required for multibyte code points.
            if (chunk.and(ALL_ASCII_MASK).compare(EQ, 0.toByte()).allTrue()) {
                errors = errors or previousIncomplete
            } else {
                previousIncomplete = chunk.compare(UGE, INCOMPLETE_CHECK).toLong()
                // Shift the input forward by four bytes to make space for the previous four bytes.
                // The previous three bytes are required for validation, pulling in the last integer
                // will give the previous four bytes. The switch to integer vectors is to allow for
                // integer shifting instead of the more expensive shuffle / slice operations.
                val chunkWithPreviousFourBytes: IntVector = chunkAsInts
                    .rearrange(FOUR_BYTES_FORWARD_SHIFT)
                    .withLane(0, previousFourUtf8Bytes)
                // Shift the current input forward by one byte to include one byte from the previous chunk.
                val previousOneByte: ByteVector = chunkAsInts
                    .lanewise(LSHL, Byte.SIZE_BITS)
                    .or(chunkWithPreviousFourBytes.lanewise(LSHR, THREE_BYTES_SIZE))
                    .reinterpretAsBytes()
                val byte2HighNibbles: ByteVector = chunkAsInts.lanewise(LSHR, 4)
                    .reinterpretAsBytes()
                    .and(LOW_NIBBLE_MASK)
                val byte1HighNibbles: ByteVector = previousOneByte.reinterpretAsInts()
                    .lanewise(LSHR, 4)
                    .reinterpretAsBytes()
                    .and(LOW_NIBBLE_MASK)
                val byte1LowNibbles: ByteVector = previousOneByte.and(LOW_NIBBLE_MASK)
                val byte1HighState: ByteVector = byte1HighNibbles.selectFrom(BYTE_1_HIGH_LOOKUP)
                val byte1LowState: ByteVector = byte1LowNibbles.selectFrom(BYTE_1_LOW_LOOKUP)
                val byte2HighState: ByteVector = byte2HighNibbles.selectFrom(BYTE_2_HIGH_LOOKUP)
                val firstCheck: ByteVector = byte1HighState.and(byte1LowState).and(byte2HighState)
                // All remaining checks are for invalid 3 and 4-byte sequences, which either have too many
                // continuation bytes or not enough.
                val previousTwoBytes: ByteVector = chunkAsInts
                    .lanewise(LSHL, TWO_BYTES_SIZE)
                    .or(chunkWithPreviousFourBytes.lanewise(LSHR, TWO_BYTES_SIZE))
                    .reinterpretAsBytes()
                // The minimum leading byte of 3-byte sequences is always greater than the maximum leading byte of 2-byte sequences.
                val is3ByteLead: VectorMask<Byte> = previousTwoBytes.compare(UGT, MAX_2_LEADING_BYTE)
                val previousThreeBytes: ByteVector = chunkAsInts
                    .lanewise(LSHL, THREE_BYTES_SIZE)
                    .or(chunkWithPreviousFourBytes.lanewise(LSHR, Byte.SIZE_BITS))
                    .reinterpretAsBytes()
                // The minimum leading byte of 4-byte sequences is always greater than the maximum leading byte of 3-byte sequences.
                val is4ByteLead: VectorMask<Byte> = previousThreeBytes.compare(UGT, MAX_3_LEADING_BYTE)
                // The firstCheck vector contains 0x80 values on continuation byte indexes.
                // The leading bytes of 3 and 4-byte sequences should match up with these indexes and zero them out.
                val secondCheck: ByteVector = firstCheck.add(0x80.toByte(), is3ByteLead.or(is4ByteLead))
                errors = errors or secondCheck.compare(NE, 0.toByte()).toLong()
            }
            previousFourUtf8Bytes = chunkAsInts.lane(VectorUtils.INT_SPECIES.length() - 1)
            offset += STEP_SIZE
        }

        // If the input file doesn't align with the vector width, pad the missing bytes with zeros.
        val remainingBytes: VectorMask<Byte> = VectorUtils.BYTE_SPECIES.indexInRange(offset, length)
        val chunk = ByteVector.fromArray(VectorUtils.BYTE_SPECIES, buffer, offset, remainingBytes)
        if (!chunk.and(ALL_ASCII_MASK).compare(EQ, 0.toByte()).allTrue()) {
            val chunkAsInts: IntVector = chunk.reinterpretAsInts()
            previousIncomplete = chunk.compare(UGE, INCOMPLETE_CHECK).toLong()
            // Shift the input forward by four bytes to make space for the previous four bytes.
            // The previous three bytes are required for validation, pulling in the last integer
            // will give the previous four bytes. The switch to integer vectors is to allow for
            // integer shifting instead of the more expensive shuffle / slice operations.
            val chunkWithPreviousFourBytes: IntVector = chunkAsInts
                .rearrange(FOUR_BYTES_FORWARD_SHIFT)
                .withLane(0, previousFourUtf8Bytes)
            // Shift the current input forward by one byte to include one byte from the previous chunk.
            val previousOneByte: ByteVector = chunkAsInts
                .lanewise(LSHL, Byte.SIZE_BITS)
                .or(chunkWithPreviousFourBytes.lanewise(LSHR, THREE_BYTES_SIZE))
                .reinterpretAsBytes()
            val byte2HighNibbles: ByteVector = chunkAsInts.lanewise(LSHR, 4)
                .reinterpretAsBytes()
                .and(LOW_NIBBLE_MASK)
            val byte1HighNibbles: ByteVector = previousOneByte.reinterpretAsInts()
                .lanewise(LSHR, 4)
                .reinterpretAsBytes()
                .and(LOW_NIBBLE_MASK)
            val byte1LowNibbles: ByteVector = previousOneByte.and(LOW_NIBBLE_MASK)
            val byte1HighState: ByteVector = byte1HighNibbles.selectFrom(BYTE_1_HIGH_LOOKUP)
            val byte1LowState: ByteVector = byte1LowNibbles.selectFrom(BYTE_1_LOW_LOOKUP)
            val byte2HighState: ByteVector = byte2HighNibbles.selectFrom(BYTE_2_HIGH_LOOKUP)
            val firstCheck: ByteVector = byte1HighState.and(byte1LowState).and(byte2HighState)
            // All remaining checks are for invalid 3 and 4-byte sequences, which either have too many
            // continuation bytes or not enough.
            val previousTwoBytes: ByteVector = chunkAsInts
                .lanewise(LSHL, TWO_BYTES_SIZE)
                .or(chunkWithPreviousFourBytes.lanewise(LSHR, TWO_BYTES_SIZE))
                .reinterpretAsBytes()
            // The minimum leading byte of 3-byte sequences is always greater than the maximum leading byte of 2-byte sequences.
            val is3ByteLead: VectorMask<Byte> = previousTwoBytes.compare(UGT, MAX_2_LEADING_BYTE)
            val previousThreeBytes: ByteVector = chunkAsInts
                .lanewise(LSHL, THREE_BYTES_SIZE)
                .or(chunkWithPreviousFourBytes.lanewise(LSHR, Byte.SIZE_BITS))
                .reinterpretAsBytes()
            // The minimum leading byte of 4-byte sequences is always greater than the maximum leading byte of 3-byte sequences.
            val is4ByteLead: VectorMask<Byte> = previousThreeBytes.compare(UGT, MAX_3_LEADING_BYTE)
            // The firstCheck vector contains 0x80 values on continuation byte indexes.
            // The leading bytes of 3 and 4-byte sequences should match up with these indexes and zero them out.
            val secondCheck: ByteVector = firstCheck.add(0x80.toByte(), is3ByteLead.or(is4ByteLead))
            errors = errors or secondCheck.compare(NE, 0.toByte()).toLong()
        }

        if ((errors or previousIncomplete) != 0L) {
            throw JsonParsingException("The input is not valid UTF-8")
        }
    }

    private fun createIncompleteCheck(): ByteVector {
        // Previous vector is in an incomplete state if the last byte is smaller than 0xC0,
        // or the second last byte is smaller than 0xE0, or the third last byte is smaller than 0xF0.
        val vectorByteSize = VectorUtils.BYTE_SPECIES.vectorByteSize()
        val eofArray = ByteArray(vectorByteSize) { 0xFF.toByte() }
        eofArray[vectorByteSize - 3] = 0xF0.toByte()
        eofArray[vectorByteSize - 2] = 0xE0.toByte()
        eofArray[vectorByteSize - 1] = 0xC0.toByte()
        return ByteVector.fromArray(VectorUtils.BYTE_SPECIES, eofArray, 0)
    }

    private fun createByte1HighLookup(): ByteVector {
        val byte1HighArray = byteArrayOf(
            // ASCII high nibble = 0000 -> 0111, ie 0 -> 7 index in lookup table
            TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
            // Continuation high nibble = 1000 -> 1011
            TWO_CONTINUATIONS, TWO_CONTINUATIONS, TWO_CONTINUATIONS, TWO_CONTINUATIONS,
            // Two byte lead high nibble = 1100 -> 1101
            TOO_SHORT bor OVERLONG_2BYTE, TOO_SHORT,
            // Three byte lead high nibble = 1110
            TOO_SHORT bor OVERLONG_3BYTE bor SURROGATE,
            // Four byte lead high nibble = 1111
            TOO_SHORT bor TOO_LARGE bor TOO_LARGE_1000 bor OVERLONG_4BYTE
        )
        return alignArrayToVector(byte1HighArray)
    }

    private fun createByte1LowLookup(): ByteVector {
        val CARRY: Byte = TOO_SHORT bor TOO_LONG bor TWO_CONTINUATIONS
        val byte1LowArray = byteArrayOf(
            // ASCII, two byte lead and three byte leading low nibble = 0000 -> 1111,
            // Four byte lead low nibble = 0000 -> 0111.
            // Continuation byte low nibble is inconsequential
            // Low nibble does not affect the states TOO_SHORT, TOO_LONG, TWO_CONTINUATIONS, so they will
            // be carried over regardless.
            CARRY bor OVERLONG_2BYTE bor OVERLONG_3BYTE bor OVERLONG_4BYTE,
            // 0001
            CARRY bor OVERLONG_2BYTE,
            CARRY,
            CARRY,
            // 1111_0100 -> 1111 = TOO_LARGE range
            CARRY bor TOO_LARGE,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            // 1110_1101
            CARRY bor TOO_LARGE bor TOO_LARGE_1000 bor SURROGATE,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000,
            CARRY bor TOO_LARGE bor TOO_LARGE_1000
        )
        return alignArrayToVector(byte1LowArray)
    }

    private fun createByte2HighLookup(): ByteVector {
        val byte2HighArray = byteArrayOf(
            // ASCII high nibble = 0000 -> 0111, ie 0 -> 7 index in lookup table
            TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
            // Continuation high nibble - 1000 -> 1011
            TOO_LONG bor TWO_CONTINUATIONS bor OVERLONG_2BYTE bor OVERLONG_3BYTE bor OVERLONG_4BYTE bor TOO_LARGE_1000,
            TOO_LONG bor TWO_CONTINUATIONS bor OVERLONG_2BYTE bor OVERLONG_3BYTE bor TOO_LARGE,
            TOO_LONG bor TWO_CONTINUATIONS bor OVERLONG_2BYTE bor SURROGATE bor TOO_LARGE,
            TOO_LONG bor TWO_CONTINUATIONS bor OVERLONG_2BYTE bor SURROGATE bor TOO_LARGE,
            // 1100 -> 1111 = unexpected leading byte
            TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT
        )
        return alignArrayToVector(byte2HighArray)
    }

    private fun alignArrayToVector(arrayValues: ByteArray): ByteVector {
        // Pad array with zeroes to align up with vector size.
        val alignedArray = ByteArray(VectorUtils.BYTE_SPECIES.vectorByteSize())
        arrayValues.copyInto(alignedArray)
        return ByteVector.fromArray(VectorUtils.BYTE_SPECIES, alignedArray, 0)
    }
}
