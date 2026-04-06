package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StructuralIndexerTest {

    @Test
    fun `unquoted string`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "abc 123".encodeToByteArray()

        indexer.index(bytes, bytes.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.getAndAdvance() shouldBe 4
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `quoted string`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "\"abc 123\"".encodeToByteArray()

        indexer.index(bytes, bytes.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `unclosed string`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "\"abc 123".encodeToByteArray()

        val ex = shouldThrow<JsonParsingException> {
            indexer.index(bytes, bytes.size)
        }
        ex.message shouldBe "Unclosed string. A string is opened, but never closed."
    }

    @Test
    fun `quoted string spanning multiple blocks`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes =
            "abc \"a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 c0 c1 c2 c3 c4 c5 c6 c7 c8 c9 d0 d1 d2 d3 d4 d5 d6 d7 d8 d\" def".encodeToByteArray()

        indexer.index(bytes, bytes.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.getAndAdvance() shouldBe 4
        bitIndexes.getAndAdvance() shouldBe 125
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `escaped quote`() {
        for (input in listOf("abc \\\"123", "abc \\\\\\\"123")) {
            val bitIndexes = BitIndexes(1024)
            val indexer = StructuralIndexer(bitIndexes)
            val bytes = input.encodeToByteArray()

            indexer.index(bytes, bytes.size)

            bitIndexes.isEnd() shouldBe false
            bitIndexes.getAndAdvance() shouldBe 0
            bitIndexes.getAndAdvance() shouldBe 4
            bitIndexes.isEnd() shouldBe true
        }
    }

    @Test
    fun `escaped quote spanning multiple blocks`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "a0ba1ca2ca3ca4ca5ca6ca7ca8ca9cb0cb1cb2cb3cb4cb5cb6cb7cb8cb9cc0 \\\"def".encodeToByteArray()

        indexer.index(bytes, bytes.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.getAndAdvance() shouldBe 63
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `unescaped quote`() {
        for (input in listOf("abc \\\\\"123", "abc \\\\\\\\\"123")) {
            val bitIndexes = BitIndexes(1024)
            val indexer = StructuralIndexer(bitIndexes)
            val bytes = input.encodeToByteArray()

            val ex = shouldThrow<JsonParsingException> {
                indexer.index(bytes, bytes.size)
            }
            ex.message shouldBe "Unclosed string. A string is opened, but never closed."
        }
    }

    @Test
    fun `unescaped quote spanning multiple blocks`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 c0 \\\\\"abc".encodeToByteArray()

        val ex = shouldThrow<JsonParsingException> {
            indexer.index(bytes, bytes.size)
        }
        ex.message shouldBe "Unclosed string. A string is opened, but never closed."
    }

    @Test
    fun `operators classification`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "a{bc}1:2,3[efg]aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".encodeToByteArray()

        indexer.index(bytes, bytes.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.getAndAdvance() shouldBe 1
        bitIndexes.getAndAdvance() shouldBe 2
        bitIndexes.getAndAdvance() shouldBe 4
        bitIndexes.getAndAdvance() shouldBe 5
        bitIndexes.getAndAdvance() shouldBe 6
        bitIndexes.getAndAdvance() shouldBe 7
        bitIndexes.getAndAdvance() shouldBe 8
        bitIndexes.getAndAdvance() shouldBe 9
        bitIndexes.getAndAdvance() shouldBe 10
        bitIndexes.getAndAdvance() shouldBe 11
        bitIndexes.getAndAdvance() shouldBe 14
        bitIndexes.getAndAdvance() shouldBe 15
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `control characters classification`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val input = ByteArray(64) { 'a'.code.toByte() }.also {
            it[3] = 0x1a
            it[5] = 0x0c
        }

        indexer.index(input, input.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.getAndAdvance() shouldBe 3
        bitIndexes.getAndAdvance() shouldBe 4
        bitIndexes.getAndAdvance() shouldBe 5
        bitIndexes.getAndAdvance() shouldBe 6
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `whitespaces classification`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)
        val bytes = "a bc\t1\n2\r3efgaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".encodeToByteArray()

        indexer.index(bytes, bytes.size)

        bitIndexes.isEnd() shouldBe false
        bitIndexes.getAndAdvance() shouldBe 0
        bitIndexes.getAndAdvance() shouldBe 2
        bitIndexes.getAndAdvance() shouldBe 5
        bitIndexes.getAndAdvance() shouldBe 7
        bitIndexes.getAndAdvance() shouldBe 9
        bitIndexes.isEnd() shouldBe true
    }

    @Test
    fun `input length close to vector width`() {
        val inputs = listOf(
            "aaaaaaaaaaaaaaa",                                                     // 120 bits
            "aaaaaaaaaaaaaaaa",                                                    // 128 bits
            "aaaaaaaaaaaaaaaaa",                                                   // 136 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",                                     // 248 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",                                    // 256 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",                                   // 264 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",       // 504 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",      // 512 bits
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",     // 520 bits
        )
        for (input in inputs) {
            val bitIndexes = BitIndexes(1024)
            val indexer = StructuralIndexer(bitIndexes)
            val bytes = input.encodeToByteArray()

            indexer.index(bytes, bytes.size)

            withClue("Failed for input length ${bytes.size}") {
                bitIndexes.isEnd() shouldBe false
                bitIndexes.getAndAdvance() shouldBe 0
                bitIndexes.isEnd() shouldBe true
            }
        }
    }

    @Test
    fun `empty input`() {
        val bitIndexes = BitIndexes(1024)
        val indexer = StructuralIndexer(bitIndexes)

        indexer.index("".encodeToByteArray(), 0)

        bitIndexes.isEnd() shouldBe true
    }
}
