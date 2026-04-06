package io.github.devcrocod.simdjson

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.ByteVector.SPECIES_256
import jdk.incubator.vector.ByteVector.SPECIES_512
import jdk.incubator.vector.VectorOperators.ULE
import jdk.incubator.vector.VectorShuffle

internal class StructuralIndexer(private val bitIndexes: BitIndexes) {

    private val lastBlock = ByteArray(STEP_SIZE)

    fun index(buffer: ByteArray, length: Int) {
        Utf8Validator.validate(buffer, length)
        bitIndexes.reset()
        when (VECTOR_BIT_SIZE) {
            256 -> index256(buffer, length)
            512 -> index512(buffer, length)
            else -> throw UnsupportedOperationException("Unsupported vector width: ${VECTOR_BIT_SIZE * 64}")
        }
    }

    private fun index256(buffer: ByteArray, length: Int) {
        var prevInString = 0L
        var prevEscaped = 0L
        var prevStructurals = 0L
        var unescapedCharsError = 0L
        var prevScalar = 0L

        // Using SPECIES_512 here is not a mistake. Each iteration of the below loop processes two 256-bit chunks,
        // so effectively it processes 512 bits at once.
        val loopBound = SPECIES_512.loopBound(length)
        var offset = 0
        var blockIndex = 0
        while (offset < loopBound) {
            val chunk0 = ByteVector.fromArray(SPECIES_256, buffer, offset)
            val chunk1 = ByteVector.fromArray(SPECIES_256, buffer, offset + 32)

            // string scanning
            val backslash0 = chunk0.eq(BACKSLASH).toLong()
            val backslash1 = chunk1.eq(BACKSLASH).toLong()
            var backslash = backslash0 or (backslash1 shl 32)

            val escaped: Long
            if (backslash == 0L) {
                escaped = prevEscaped
                prevEscaped = 0
            } else {
                backslash = backslash and prevEscaped.inv()
                val followsEscape = (backslash shl 1) or prevEscaped
                val oddSequenceStarts = backslash and ODD_BITS_MASK and followsEscape.inv()

                val sequencesStartingOnEvenBits = oddSequenceStarts + backslash
                // Here, we check if the unsigned addition above caused an overflow. If that's the case, we store 1 in prevEscaped.
                // The formula used to detect overflow was taken from 'Hacker's Delight, Second Edition' by Henry S. Warren, Jr.,
                // Chapter 2-13.
                prevEscaped =
                    ((oddSequenceStarts ushr 1) + (backslash ushr 1) + ((oddSequenceStarts and backslash) and 1L)) ushr 63

                val invertMask = sequencesStartingOnEvenBits shl 1
                escaped = (EVEN_BITS_MASK xor invertMask) and followsEscape
            }

            val unescaped0 = chunk0.compare(ULE, LAST_CONTROL_CHARACTER).toLong()
            val unescaped1 = chunk1.compare(ULE, LAST_CONTROL_CHARACTER).toLong()
            val unescaped = unescaped0 or (unescaped1 shl 32)

            val quote0 = chunk0.eq(QUOTE).toLong()
            val quote1 = chunk1.eq(QUOTE).toLong()
            val quote = (quote0 or (quote1 shl 32)) and escaped.inv()

            val inString = prefixXor(quote) xor prevInString
            prevInString = inString shr 63

            // characters classification
            val chunk0Low: VectorShuffle<Byte> = chunk0.and(LOW_NIBBLE_MASK).toShuffle()
            val chunk1Low: VectorShuffle<Byte> = chunk1.and(LOW_NIBBLE_MASK).toShuffle()

            val whitespace0 = chunk0.eq(WHITESPACE_TABLE.rearrange(chunk0Low)).toLong()
            val whitespace1 = chunk1.eq(WHITESPACE_TABLE.rearrange(chunk1Low)).toLong()
            val whitespace = whitespace0 or (whitespace1 shl 32)

            val curlified0 = chunk0.or(0x20.toByte())
            val curlified1 = chunk1.or(0x20.toByte())
            val op0 = curlified0.eq(OP_TABLE.rearrange(chunk0Low)).toLong()
            val op1 = curlified1.eq(OP_TABLE.rearrange(chunk1Low)).toLong()
            val op = op0 or (op1 shl 32)

            // finish
            val scalar = (op or whitespace).inv()
            val nonQuoteScalar = scalar and quote.inv()
            val followsNonQuoteScalar = (nonQuoteScalar shl 1) or prevScalar
            prevScalar = nonQuoteScalar ushr 63
            val potentialScalarStart = scalar and followsNonQuoteScalar.inv()
            val potentialStructuralStart = op or potentialScalarStart
            bitIndexes.write(blockIndex, prevStructurals)
            blockIndex += STEP_SIZE
            prevStructurals = potentialStructuralStart and (inString xor quote).inv()
            unescapedCharsError = unescapedCharsError or (unescaped and inString)

            offset += STEP_SIZE
        }

        val remainder = remainder(buffer, length, blockIndex)
        val chunk0 = ByteVector.fromArray(SPECIES_256, remainder, 0)
        val chunk1 = ByteVector.fromArray(SPECIES_256, remainder, 32)

        // string scanning
        val backslash0 = chunk0.eq(BACKSLASH).toLong()
        val backslash1 = chunk1.eq(BACKSLASH).toLong()
        var backslash = backslash0 or (backslash1 shl 32)

        val escaped: Long
        if (backslash == 0L) {
            escaped = prevEscaped
        } else {
            backslash = backslash and prevEscaped.inv()
            val followsEscape = (backslash shl 1) or prevEscaped
            val oddSequenceStarts = backslash and ODD_BITS_MASK and followsEscape.inv()

            val sequencesStartingOnEvenBits = oddSequenceStarts + backslash
            val invertMask = sequencesStartingOnEvenBits shl 1
            escaped = (EVEN_BITS_MASK xor invertMask) and followsEscape
        }

        val unescaped0 = chunk0.compare(ULE, LAST_CONTROL_CHARACTER).toLong()
        val unescaped1 = chunk1.compare(ULE, LAST_CONTROL_CHARACTER).toLong()
        val unescaped = unescaped0 or (unescaped1 shl 32)

        val quote0 = chunk0.eq(QUOTE).toLong()
        val quote1 = chunk1.eq(QUOTE).toLong()
        val quote = (quote0 or (quote1 shl 32)) and escaped.inv()

        val inString = prefixXor(quote) xor prevInString
        prevInString = inString shr 63

        // characters classification
        val chunk0Low: VectorShuffle<Byte> = chunk0.and(LOW_NIBBLE_MASK).toShuffle()
        val chunk1Low: VectorShuffle<Byte> = chunk1.and(LOW_NIBBLE_MASK).toShuffle()

        val whitespace0 = chunk0.eq(WHITESPACE_TABLE.rearrange(chunk0Low)).toLong()
        val whitespace1 = chunk1.eq(WHITESPACE_TABLE.rearrange(chunk1Low)).toLong()
        val whitespace = whitespace0 or (whitespace1 shl 32)

        val curlified0 = chunk0.or(0x20.toByte())
        val curlified1 = chunk1.or(0x20.toByte())
        val op0 = curlified0.eq(OP_TABLE.rearrange(chunk0Low)).toLong()
        val op1 = curlified1.eq(OP_TABLE.rearrange(chunk1Low)).toLong()
        val op = op0 or (op1 shl 32)

        // finish
        val scalar = (op or whitespace).inv()
        val nonQuoteScalar = scalar and quote.inv()
        val followsNonQuoteScalar = (nonQuoteScalar shl 1) or prevScalar
        val potentialScalarStart = scalar and followsNonQuoteScalar.inv()
        val potentialStructuralStart = op or potentialScalarStart
        bitIndexes.write(blockIndex, prevStructurals)
        blockIndex += STEP_SIZE
        prevStructurals = potentialStructuralStart and (inString xor quote).inv()
        unescapedCharsError = unescapedCharsError or (unescaped and inString)
        bitIndexes.write(blockIndex, prevStructurals)
        bitIndexes.finish()
        if (prevInString != 0L) {
            throw JsonParsingException("Unclosed string. A string is opened, but never closed.")
        }
        if (unescapedCharsError != 0L) {
            throw JsonParsingException("Unescaped characters. Within strings, there are characters that should be escaped.")
        }
    }

    private fun index512(buffer: ByteArray, length: Int) {
        var prevInString = 0L
        var prevEscaped = 0L
        var prevStructurals = 0L
        var unescapedCharsError = 0L
        var prevScalar = 0L

        val loopBound = SPECIES_512.loopBound(length)
        var offset = 0
        var blockIndex = 0
        while (offset < loopBound) {
            val chunk = ByteVector.fromArray(SPECIES_512, buffer, offset)

            // string scanning
            var backslash = chunk.eq(BACKSLASH).toLong()

            val escaped: Long
            if (backslash == 0L) {
                escaped = prevEscaped
                prevEscaped = 0
            } else {
                backslash = backslash and prevEscaped.inv()
                val followsEscape = (backslash shl 1) or prevEscaped
                val oddSequenceStarts = backslash and ODD_BITS_MASK and followsEscape.inv()

                val sequencesStartingOnEvenBits = oddSequenceStarts + backslash
                // Here, we check if the unsigned addition above caused an overflow. If that's the case, we store 1 in prevEscaped.
                // The formula used to detect overflow was taken from 'Hacker's Delight, Second Edition' by Henry S. Warren, Jr.,
                // Chapter 2-13.
                prevEscaped =
                    ((oddSequenceStarts ushr 1) + (backslash ushr 1) + ((oddSequenceStarts and backslash) and 1L)) ushr 63

                val invertMask = sequencesStartingOnEvenBits shl 1
                escaped = (EVEN_BITS_MASK xor invertMask) and followsEscape
            }

            val unescaped = chunk.compare(ULE, LAST_CONTROL_CHARACTER).toLong()
            val quote = chunk.eq(QUOTE).toLong() and escaped.inv()
            val inString = prefixXor(quote) xor prevInString
            prevInString = inString shr 63

            // characters classification
            val chunkLow: VectorShuffle<Byte> = chunk.and(LOW_NIBBLE_MASK).toShuffle()
            val whitespace = chunk.eq(WHITESPACE_TABLE.rearrange(chunkLow)).toLong()
            val curlified = chunk.or(0x20.toByte())
            val op = curlified.eq(OP_TABLE.rearrange(chunkLow)).toLong()

            // finish
            val scalar = (op or whitespace).inv()
            val nonQuoteScalar = scalar and quote.inv()
            val followsNonQuoteScalar = (nonQuoteScalar shl 1) or prevScalar
            prevScalar = nonQuoteScalar ushr 63
            val potentialScalarStart = scalar and followsNonQuoteScalar.inv()
            val potentialStructuralStart = op or potentialScalarStart
            bitIndexes.write(blockIndex, prevStructurals)
            blockIndex += STEP_SIZE
            prevStructurals = potentialStructuralStart and (inString xor quote).inv()
            unescapedCharsError = unescapedCharsError or (unescaped and inString)

            offset += STEP_SIZE
        }

        val remainder = remainder(buffer, length, blockIndex)
        val chunk = ByteVector.fromArray(SPECIES_512, remainder, 0)

        // string scanning
        var backslash = chunk.eq(BACKSLASH).toLong()

        val escaped: Long
        if (backslash == 0L) {
            escaped = prevEscaped
        } else {
            backslash = backslash and prevEscaped.inv()
            val followsEscape = (backslash shl 1) or prevEscaped
            val oddSequenceStarts = backslash and ODD_BITS_MASK and followsEscape.inv()

            val sequencesStartingOnEvenBits = oddSequenceStarts + backslash
            val invertMask = sequencesStartingOnEvenBits shl 1
            escaped = (EVEN_BITS_MASK xor invertMask) and followsEscape
        }

        val unescaped = chunk.compare(ULE, LAST_CONTROL_CHARACTER).toLong()
        val quote = chunk.eq(QUOTE).toLong() and escaped.inv()
        val inString = prefixXor(quote) xor prevInString
        prevInString = inString shr 63

        // characters classification
        val chunkLow: VectorShuffle<Byte> = chunk.and(LOW_NIBBLE_MASK).toShuffle()
        val whitespace = chunk.eq(WHITESPACE_TABLE.rearrange(chunkLow)).toLong()
        val curlified = chunk.or(0x20.toByte())
        val op = curlified.eq(OP_TABLE.rearrange(chunkLow)).toLong()

        // finish
        val scalar = (op or whitespace).inv()
        val nonQuoteScalar = scalar and quote.inv()
        val followsNonQuoteScalar = (nonQuoteScalar shl 1) or prevScalar
        val potentialScalarStart = scalar and followsNonQuoteScalar.inv()
        val potentialStructuralStart = op or potentialScalarStart
        bitIndexes.write(blockIndex, prevStructurals)
        blockIndex += STEP_SIZE
        prevStructurals = potentialStructuralStart and (inString xor quote).inv()
        unescapedCharsError = unescapedCharsError or (unescaped and inString)
        bitIndexes.write(blockIndex, prevStructurals)
        bitIndexes.finish()
        if (prevInString != 0L) {
            throw JsonParsingException("Unclosed string. A string is opened, but never closed.")
        }
        if (unescapedCharsError != 0L) {
            throw JsonParsingException("Unescaped characters. Within strings, there are characters that should be escaped.")
        }
    }

    private fun remainder(buffer: ByteArray, length: Int, idx: Int): ByteArray {
        LAST_BLOCK_SPACES.copyInto(lastBlock)
        buffer.copyInto(lastBlock, destinationOffset = 0, startIndex = idx, endIndex = length)
        return lastBlock
    }

    companion object {
        private val VECTOR_BIT_SIZE = VectorUtils.BYTE_SPECIES.vectorBitSize()
        private const val STEP_SIZE = 64
        private val BACKSLASH = '\\'.code.toByte()
        private val QUOTE = '"'.code.toByte()
        private const val SPACE: Byte = 0x20
        private val LAST_CONTROL_CHARACTER = 0x1F.toByte()
        private const val EVEN_BITS_MASK = 0x5555555555555555L
        private val ODD_BITS_MASK = EVEN_BITS_MASK.inv()
        private val LOW_NIBBLE_MASK = 0x0f.toByte()
        private val WHITESPACE_TABLE: ByteVector = VectorUtils.repeat(
            byteArrayOf(
                ' '.code.toByte(), 100, 100, 100, 17, 100, 113, 2,
                100, '\t'.code.toByte(), '\n'.code.toByte(), 112, 100, '\r'.code.toByte(), 100, 100
            )
        )
        private val OP_TABLE: ByteVector = VectorUtils.repeat(
            byteArrayOf(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                ':'.code.toByte(), '{'.code.toByte(), ','.code.toByte(), '}'.code.toByte(), 0, 0
            )
        )
        private val LAST_BLOCK_SPACES = ByteArray(STEP_SIZE) { SPACE }

        private fun prefixXor(bitmask: Long): Long {
            var b = bitmask
            b = b xor (b shl 1)
            b = b xor (b shl 2)
            b = b xor (b shl 4)
            b = b xor (b shl 8)
            b = b xor (b shl 16)
            b = b xor (b shl 32)
            return b
        }
    }
}
