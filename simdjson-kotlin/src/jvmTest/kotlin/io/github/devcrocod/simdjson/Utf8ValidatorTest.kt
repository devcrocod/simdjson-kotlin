package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test

class Utf8ValidatorTest {

    @Test
    fun `valid UTF-8`() {
        val input = randomUtf8ByteArray()

        try {
            Utf8Validator.validate(input, input.size)
        } catch (ex: JsonParsingException) {
            if (ex.message == "The input is not valid UTF-8") {
                throw AssertionError("Failed for input: ${toHexString(input)}", ex)
            }
        }
    }

    @Test
    fun `invalid ASCII`() {
        for (invalidAsciiByte in 128..255) {
            val input = randomUtf8ByteArrayIncluding(invalidAsciiByte.toByte())

            withClue("Failed for input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `continuation byte without preceding leading byte`() {
        for (continuationByte in 0b10_000000..0b10_111111) {
            val input = randomUtf8ByteArrayIncluding(continuationByte.toByte())

            withClue("Failed for input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `two-byte sequence with two continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(
            0b110_00010.toByte(),
            0b10_000000.toByte(),
            0b10_000000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `two-byte sequence without continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(0b110_00010.toByte())

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `two-byte sequence without continuation bytes at the end`() {
        val input = randomUtf8ByteArrayEndedWith(0b110_00010.toByte())

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `three-byte sequence with three continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(
            0b1110_0000.toByte(),
            0b10_100000.toByte(),
            0b10_000000.toByte(),
            0b10_000000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `three-byte sequence with one continuation byte`() {
        val input = randomUtf8ByteArrayIncluding(
            0b1110_0000.toByte(),
            0b10_100000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `three-byte sequence without continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(0b1110_0000.toByte())

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `three-byte sequence with one continuation byte at the end`() {
        val input = randomUtf8ByteArrayEndedWith(
            0b1110_0000.toByte(),
            0b10_100000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `three-byte sequence without continuation bytes at the end`() {
        val input = randomUtf8ByteArrayEndedWith(0b1110_0000.toByte())

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence with four continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(
            0b11110_000.toByte(),
            0b10_010000.toByte(),
            0b10_000000.toByte(),
            0b10_000000.toByte(),
            0b10_000000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence with two continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(
            0b11110_000.toByte(),
            0b10_010000.toByte(),
            0b10_000000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence with one continuation byte`() {
        val input = randomUtf8ByteArrayIncluding(
            0b11110_000.toByte(),
            0b10_010000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence without continuation bytes`() {
        val input = randomUtf8ByteArrayIncluding(0b11110_000.toByte())

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence with two continuation bytes at the end`() {
        val input = randomUtf8ByteArrayEndedWith(
            0b11110_000.toByte(),
            0b10_010000.toByte(),
            0b10_000000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence with one continuation byte at the end`() {
        val input = randomUtf8ByteArrayEndedWith(
            0b11110_000.toByte(),
            0b10_010000.toByte()
        )

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `four-byte sequence without continuation bytes at the end`() {
        val input = randomUtf8ByteArrayEndedWith(0b11110_000.toByte())

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `overlong two-byte sequence`() {
        val sequences = utf8Sequences(0x0000, 0x007F, 2)

        for (sequence in sequences) {
            val input = randomUtf8ByteArrayIncluding(*sequence)

            withClue("Failed for sequence: ${toHexString(sequence)} and input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `overlong three-byte sequence`() {
        val sequences = utf8Sequences(0x0000, 0x07FF, 3)

        for (sequence in sequences) {
            val input = randomUtf8ByteArrayIncluding(*sequence)

            withClue("Failed for sequence: ${toHexString(sequence)} and input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `surrogate code points`() {
        val sequences = utf8Sequences(0xD800, 0xDFFF, 3)

        for (sequence in sequences) {
            val input = randomUtf8ByteArrayIncluding(*sequence)

            withClue("Failed for sequence: ${toHexString(sequence)} and input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `overlong four-byte sequence`() {
        val sequences = utf8Sequences(0x0000, 0xFFFF, 4)

        for (sequence in sequences) {
            val input = randomUtf8ByteArrayIncluding(*sequence)

            withClue("Failed for sequence: ${toHexString(sequence)} and input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `too large four-byte sequence`() {
        val sequences = utf8Sequences(0x110000, 0x110400, 4)

        for (sequence in sequences) {
            val input = randomUtf8ByteArrayIncluding(*sequence)

            withClue("Failed for sequence: ${toHexString(sequence)} and input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- Valid multi-byte sequences (hardcoded, deterministic) ---

    @Test
    fun `valid ASCII only`() {
        val input = "Hello, World!".toByteArray(Charsets.UTF_8)
        shouldNotThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
    }

    @Test
    fun `valid 2-byte sequences - boundary values`() {
        // U+0080 (min 2-byte): C2 80
        // U+07FF (max 2-byte): DF BF
        // U+00F1 (n-tilde): C3 B1
        val sequences = arrayOf(
            byteArrayOf(0xC2.toByte(), 0x80.toByte()),
            byteArrayOf(0xDF.toByte(), 0xBF.toByte()),
            byteArrayOf(0xC3.toByte(), 0xB1.toByte()),
        )
        for (seq in sequences) {
            val input = randomUtf8ByteArrayIncluding(*seq)
            withClue("Failed for valid 2-byte sequence: ${toHexString(seq)}, input: ${toHexString(input)}") {
                shouldNotThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
            }
        }
    }

    @Test
    fun `valid 3-byte sequences - boundary values`() {
        // U+0800 (min 3-byte): E0 A0 80
        // U+FFFF (max 3-byte, excluding surrogates): EF BF BF
        // U+FEFF (BOM): EF BB BF
        // U+E000 (first private use area): EE 80 80
        // U+20A1 (colon sign): E2 82 A1
        val sequences = arrayOf(
            byteArrayOf(0xE0.toByte(), 0xA0.toByte(), 0x80.toByte()),
            byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBF.toByte()),
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            byteArrayOf(0xEE.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xE2.toByte(), 0x82.toByte(), 0xA1.toByte()),
        )
        for (seq in sequences) {
            val input = randomUtf8ByteArrayIncluding(*seq)
            withClue("Failed for valid 3-byte sequence: ${toHexString(seq)}, input: ${toHexString(input)}") {
                shouldNotThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
            }
        }
    }

    @Test
    fun `valid 4-byte sequences - boundary values`() {
        // U+10000 (min 4-byte): F0 90 80 80
        // U+10FFFF (max valid): F4 8F BF BF
        // U+1030C (Deseret): F0 90 8C BC
        val sequences = arrayOf(
            byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xF4.toByte(), 0x8F.toByte(), 0xBF.toByte(), 0xBF.toByte()),
            byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x8C.toByte(), 0xBC.toByte()),
        )
        for (seq in sequences) {
            val input = randomUtf8ByteArrayIncluding(*seq)
            withClue("Failed for valid 4-byte sequence: ${toHexString(seq)}, input: ${toHexString(input)}") {
                shouldNotThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
            }
        }
    }

    @Test
    fun `valid mixed multi-byte content`() {
        // Mix of 1, 2, 3, and 4-byte characters:
        // 'a' (1B) + n-tilde (2B) + colon sign (3B) + Deseret (4B)
        val input = byteArrayOf(
            0x61,                                                   // 'a'
            0xC3.toByte(), 0xB1.toByte(),                           // U+00F1
            0xE2.toByte(), 0x82.toByte(), 0xA1.toByte(),            // U+20A1
            0xF0.toByte(), 0x90.toByte(), 0x8C.toByte(), 0xBC.toByte() // U+1030C
        )
        shouldNotThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
    }

    // --- Invalid hardcoded sequences from Autobahn / C++ testsuite ---

    @Test
    fun `two-byte lead followed by ASCII instead of continuation`() {
        // C3 28 - lead byte 0xC3 followed by ASCII '(' instead of continuation
        assertInvalidUtf8(0xC3.toByte(), 0x28.toByte())
    }

    @Test
    fun `two continuation bytes without leading byte`() {
        // A0 A1 - two continuation bytes, no lead
        assertInvalidUtf8(0xA0.toByte(), 0xA1.toByte())
    }

    @Test
    fun `three-byte sequence with ASCII in second position`() {
        // E2 28 A1 - lead E2, then ASCII '(' where continuation expected
        assertInvalidUtf8(0xE2.toByte(), 0x28.toByte(), 0xA1.toByte())
    }

    @Test
    fun `three-byte sequence with ASCII in third position`() {
        // E2 82 28 - lead E2, valid continuation, then ASCII '('
        assertInvalidUtf8(0xE2.toByte(), 0x82.toByte(), 0x28.toByte())
    }

    @Test
    fun `four-byte sequence with ASCII in second position`() {
        // F0 28 8C BC - lead F0, then ASCII '(' where continuation expected
        assertInvalidUtf8(0xF0.toByte(), 0x28.toByte(), 0x8C.toByte(), 0xBC.toByte())
    }

    @Test
    fun `four-byte sequence with ASCII in third position`() {
        // F0 90 28 BC - lead F0, valid continuation, then ASCII '('
        assertInvalidUtf8(0xF0.toByte(), 0x90.toByte(), 0x28.toByte(), 0xBC.toByte())
    }

    @Test
    fun `classic overlong C0 9F`() {
        // C0 9F - overlong encoding, C0 is never valid
        assertInvalidUtf8(0xC0.toByte(), 0x9F.toByte())
    }

    @Test
    fun `start byte F5 and above`() {
        // F5 FF FF FF - F5+ is always invalid (would encode > U+10FFFF)
        assertInvalidUtf8(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
    }

    @Test
    fun `surrogate U+D800 encoded as UTF-8`() {
        // ED A0 81 - U+D801 surrogate
        assertInvalidUtf8(0xED.toByte(), 0xA0.toByte(), 0x81.toByte())
    }

    @Test
    fun `five-byte sequence`() {
        // F8 90 80 80 80 - 5-byte sequences are always invalid in UTF-8
        assertInvalidUtf8(0xF8.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte())
    }

    @Test
    fun `two-byte lead followed by 7F instead of continuation`() {
        // C2 7F - 0x7F is not a valid continuation byte (must be 10xxxxxx)
        assertInvalidUtf8(0xC2.toByte(), 0x7F.toByte())
    }

    @Test
    fun `lone continuation byte 0x80`() {
        assertInvalidUtf8(0x80.toByte())
    }

    @Test
    fun `four consecutive continuation bytes without leading byte`() {
        // 91 85 95 9E - all continuation bytes
        assertInvalidUtf8(0x91.toByte(), 0x85.toByte(), 0x95.toByte(), 0x9E.toByte())
    }

    // --- Invalid start bytes C0, C1 (always overlong) ---

    @Test
    fun `invalid two-byte leads C0 and C1`() {
        // C0 and C1 are always invalid: they would encode U+0000-U+007F which fit in 1 byte
        for (lead in 0xC0..0xC1) {
            for (cont in intArrayOf(0x80, 0x8F, 0x9F, 0xAF, 0xBF)) {
                val input = randomUtf8ByteArrayIncluding(lead.toByte(), cont.toByte())
                withClue("Failed for C0/C1 lead: ${toHexString(byteArrayOf(lead.toByte(), cont.toByte()))}") {
                    val ex = shouldThrow<JsonParsingException> {
                        Utf8Validator.validate(input, input.size)
                    }
                    ex.message shouldBe "The input is not valid UTF-8"
                }
            }
        }
    }

    // --- Invalid start bytes F5-FF ---

    @Test
    fun `invalid start bytes F5 through FF`() {
        for (startByte in 0xF5..0xFF) {
            val input = randomUtf8ByteArrayIncluding(
                startByte.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()
            )
            withClue("Failed for start byte: ${String.format("%02X", startByte.toByte())}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- 3-byte boundary value tests ---

    @Test
    fun `three-byte sequence E0 with second byte below A0 is overlong`() {
        // E0 requires second byte >= A0; anything 80-9F makes it overlong
        for (secondByte in 0x80..0x9F) {
            val input = randomUtf8ByteArrayIncluding(
                0xE0.toByte(), secondByte.toByte(), 0x80.toByte()
            )
            withClue("Failed for E0 ${String.format("%02X", secondByte.toByte())} 80") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `three-byte sequence ED with second byte A0 and above is surrogate`() {
        // ED requires second byte <= 9F; anything A0-BF encodes surrogates U+D800-U+DFFF
        for (secondByte in 0xA0..0xBF) {
            val input = randomUtf8ByteArrayIncluding(
                0xED.toByte(), secondByte.toByte(), 0x80.toByte()
            )
            withClue("Failed for ED ${String.format("%02X", secondByte.toByte())} 80") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- 4-byte boundary value tests ---

    @Test
    fun `four-byte sequence F0 with second byte below 90 is overlong`() {
        // F0 requires second byte >= 90; anything 80-8F is overlong
        for (secondByte in 0x80..0x8F) {
            val input = randomUtf8ByteArrayIncluding(
                0xF0.toByte(), secondByte.toByte(), 0x80.toByte(), 0x80.toByte()
            )
            withClue("Failed for F0 ${String.format("%02X", secondByte.toByte())} 80 80") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    @Test
    fun `four-byte sequence F4 with second byte 90 and above is too large`() {
        // F4 requires second byte <= 8F; anything 90-BF encodes > U+10FFFF
        for (secondByte in 0x90..0xBF) {
            val input = randomUtf8ByteArrayIncluding(
                0xF4.toByte(), secondByte.toByte(), 0x80.toByte(), 0x80.toByte()
            )
            withClue("Failed for F4 ${String.format("%02X", secondByte.toByte())} 80 80") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- Incomplete sequences from C++ Autobahn testsuite ---

    @Test
    fun `incomplete two-byte sequence after ASCII padding`() {
        // "123456789012345" + CE (incomplete 2-byte at position 15)
        val ascii = "123456789012345".toByteArray(Charsets.UTF_8)
        val input = ByteArray(ascii.size + 1)
        ascii.copyInto(input)
        input[ascii.size] = 0xC2.toByte()

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `incomplete three-byte sequence after ASCII padding`() {
        val ascii = "123456789012345".toByteArray(Charsets.UTF_8)
        val input = ByteArray(ascii.size + 1)
        ascii.copyInto(input)
        input[ascii.size] = 0xE1.toByte()

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    @Test
    fun `incomplete four-byte sequence after ASCII padding`() {
        val ascii = "123456789012345".toByteArray(Charsets.UTF_8)
        val input = ByteArray(ascii.size + 1)
        ascii.copyInto(input)
        input[ascii.size] = 0xF1.toByte()

        withClue("Failed for input: ${toHexString(input)}") {
            val ex = shouldThrow<JsonParsingException> {
                Utf8Validator.validate(input, input.size)
            }
            ex.message shouldBe "The input is not valid UTF-8"
        }
    }

    // --- Autobahn 6.6.x: progressively truncated valid sequences ---

    @Test
    fun `truncated multi-byte sequences from Autobahn 6_6`() {
        // CE (incomplete 2-byte from CE BA = U+03BA)
        // CE BA E1 (incomplete 3-byte after valid 2-byte)
        // CE BA E1 BD (incomplete 3-byte, 2 of 3 bytes)
        // CE BA E1 BD B9 CF (incomplete 2-byte after valid 3-byte)
        // CE BA E1 BD B9 CF 83 CE (incomplete 2-byte after two valid chars)
        val truncated = arrayOf(
            byteArrayOf(0xCE.toByte()),
            byteArrayOf(0xCE.toByte(), 0xBA.toByte(), 0xE1.toByte()),
            byteArrayOf(0xCE.toByte(), 0xBA.toByte(), 0xE1.toByte(), 0xBD.toByte()),
            byteArrayOf(0xCE.toByte(), 0xBA.toByte(), 0xE1.toByte(), 0xBD.toByte(), 0xB9.toByte(), 0xCF.toByte()),
            byteArrayOf(
                0xCE.toByte(), 0xBA.toByte(), 0xE1.toByte(), 0xBD.toByte(), 0xB9.toByte(),
                0xCF.toByte(), 0x83.toByte(), 0xCE.toByte()
            ),
            byteArrayOf(
                0xCE.toByte(), 0xBA.toByte(), 0xE1.toByte(), 0xBD.toByte(), 0xB9.toByte(),
                0xCF.toByte(), 0x83.toByte(), 0xCE.toByte(), 0xBC.toByte(), 0xCE.toByte()
            ),
        )
        for (seq in truncated) {
            withClue("Failed for truncated Autobahn sequence: ${toHexString(seq)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(seq, seq.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- Autobahn 6.14.x: lone incomplete sequences ---

    @Test
    fun `lone incomplete sequences from Autobahn 6_14`() {
        // DF (incomplete 2-byte)
        // EF BF (incomplete 3-byte, 2 of 3 bytes)
        val incomplete = arrayOf(
            byteArrayOf(0xDF.toByte()),
            byteArrayOf(0xEF.toByte(), 0xBF.toByte()),
        )
        for (seq in incomplete) {
            withClue("Failed for incomplete Autobahn 6.14 sequence: ${toHexString(seq)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(seq, seq.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- Chunk boundary crossing ---

    @Test
    fun `multi-byte sequence crossing vector chunk boundary`() {
        // Place a valid 4-byte sequence so it starts in one SIMD chunk and ends in the next.
        // Vector width is typically 32 or 64 bytes; we test both boundaries.
        for (boundary in intArrayOf(32, 64)) {
            // Start the 4-byte sequence 2 bytes before the boundary so it spans across
            val offset = boundary - 2
            val input = ByteArray(boundary + 32) // enough for two chunks
            // Fill with valid ASCII
            for (i in input.indices) input[i] = 0x61 // 'a'
            // Insert valid 4-byte sequence: F0 90 80 80 (U+10000)
            input[offset] = 0xF0.toByte()
            input[offset + 1] = 0x90.toByte()
            input[offset + 2] = 0x80.toByte()
            input[offset + 3] = 0x80.toByte()

            withClue("Failed for 4-byte sequence crossing boundary at offset $offset") {
                shouldNotThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
            }
        }
    }

    @Test
    fun `invalid sequence at vector chunk boundary`() {
        // Place an invalid byte right at typical vector width boundaries
        for (boundary in intArrayOf(32, 64)) {
            val input = ByteArray(boundary + 32)
            for (i in input.indices) input[i] = 0x61 // 'a'
            // Place lone continuation byte at boundary
            input[boundary] = 0x80.toByte()

            withClue("Failed for lone continuation at boundary $boundary") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }
    }

    // --- Brute-force bit-flip test (inspired by C++ unicode_tests.cpp) ---

    @Test
    fun `brute-force bit-flip detects corruption in valid UTF-8`() {
        val rng = Random(42)
        repeat(100) {
            val original = randomUtf8ByteArray()
            // Verify original is valid
            shouldNotThrow<JsonParsingException> {
                Utf8Validator.validate(original, original.size)
            }
            // Flip a random bit and check consistency with reference validator
            repeat(10) {
                val corrupted = original.copyOf()
                val byteIdx = rng.nextInt(corrupted.size)
                val bitIdx = rng.nextInt(8)
                corrupted[byteIdx] = (corrupted[byteIdx].toInt() xor (1 shl bitIdx)).toByte()

                val simdResult = try {
                    Utf8Validator.validate(corrupted, corrupted.size)
                    true
                } catch (_: JsonParsingException) {
                    false
                }
                val referenceResult = isValidUtf8(corrupted)

                withClue(
                    "Mismatch: SIMD=$simdResult, reference=$referenceResult, " +
                        "flipped byte=$byteIdx bit=$bitIdx, input=${toHexString(corrupted)}"
                ) {
                    simdResult shouldBe referenceResult
                }
            }
        }
    }

    // --- Large input tests ---

    @Test
    fun `large pure ASCII input`() {
        val input = ByteArray(10000) { 0x61 } // 10K of 'a'
        shouldNotThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
    }

    @Test
    fun `large input with invalid byte near the end`() {
        val input = ByteArray(10000) { 0x61 }
        input[9998] = 0x80.toByte() // lone continuation near end

        val ex = shouldThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
        ex.message shouldBe "The input is not valid UTF-8"
    }

    @Test
    fun `large input with invalid byte at the start`() {
        val input = ByteArray(10000) { 0x61 }
        input[0] = 0x80.toByte() // lone continuation at start

        val ex = shouldThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
        ex.message shouldBe "The input is not valid UTF-8"
    }

    // --- Single-byte edge cases ---

    @Test
    fun `single valid ASCII byte`() {
        val input = byteArrayOf(0x41) // 'A'
        shouldNotThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
    }

    @Test
    fun `single continuation byte`() {
        val input = byteArrayOf(0x80.toByte())
        val ex = shouldThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
        ex.message shouldBe "The input is not valid UTF-8"
    }

    @Test
    fun `single two-byte leading byte`() {
        val input = byteArrayOf(0xC2.toByte())
        val ex = shouldThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
        ex.message shouldBe "The input is not valid UTF-8"
    }

    // --- Puzzler from C++ fuzzer ---

    @Test
    fun `puzzler - lone 0x80 at offset 30 in 64-byte input`() {
        // Reproduces an issue from the C++ utf8 fuzzer:
        // 64-byte input that is all zeros except for 0x1C at offset 13 and 0x80 at offset 30.
        val input = ByteArray(64)
        input[13] = 0x1C
        input[30] = 0x80.toByte()

        val ex = shouldThrow<JsonParsingException> {
            Utf8Validator.validate(input, input.size)
        }
        ex.message shouldBe "The input is not valid UTF-8"
    }

    companion object {

        /**
         * Generates UTF-8 sequences from the provided range. Each sequence is of the given length.
         * Note that when the length is greater than necessary for a given code point, this function
         * produces sequences that are invalid UTF-8. This is a useful property when one wants to
         * generate overlong encodings for testing purposes.
         */
        private fun utf8Sequences(from: Int, to: Int, length: Int): List<ByteArray> {
            val result = mutableListOf<ByteArray>()
            for (i in from..to) {
                val bytes = ByteArray(length)
                var current = i
                // continuation bytes
                for (byteIdx in length - 1 downTo 1) {
                    bytes[byteIdx] = (0b1000_0000 or (current and 0b0011_1111)).toByte()
                    current = current ushr 6
                }
                // leading byte
                bytes[0] = ((0x80000000.toInt() shr (24 + length - 1)) or (current and 0b0011_111)).toByte()
                result.add(bytes)
            }
            return result
        }

        private fun randomUtf8ByteArray(): ByteArray {
            return randomUtf8ByteArray(1, 1000)
        }

        private fun randomUtf8ByteArrayIncluding(vararg sequence: Byte): ByteArray {
            val prefix = randomUtf8ByteArray(0, 500)
            val suffix = randomUtf8ByteArray(0, 500)
            val result = ByteArray(prefix.size + sequence.size + suffix.size)
            prefix.copyInto(result)
            sequence.copyInto(result, prefix.size)
            suffix.copyInto(result, prefix.size + sequence.size)
            return result
        }

        private fun randomUtf8ByteArrayEndedWith(vararg sequence: Byte): ByteArray {
            val array = randomUtf8ByteArray(0, 1000)
            val result = ByteArray(array.size + sequence.size)
            array.copyInto(result)
            sequence.copyInto(result, array.size)
            return result
        }

        private fun randomUtf8ByteArray(minChars: Int, maxChars: Int): ByteArray {
            val stringLen = Random.nextInt(minChars, maxChars + 1)
            val chars = CharArray(stringLen) { Random.nextInt(Char.MIN_VALUE.code, Char.MAX_VALUE.code + 1).toChar() }
            return chars.concatToString().encodeToByteArray()
        }

        private fun assertInvalidUtf8(vararg sequence: Byte) {
            val input = randomUtf8ByteArrayIncluding(*sequence)
            withClue("Failed for sequence: ${toHexString(sequence)} in input: ${toHexString(input)}") {
                val ex = shouldThrow<JsonParsingException> {
                    Utf8Validator.validate(input, input.size)
                }
                ex.message shouldBe "The input is not valid UTF-8"
            }
        }

        /**
         * Reference byte-by-byte UTF-8 validator for cross-checking the SIMD implementation.
         * Based on the C++ basic_validate_utf8() from simdjson unicode_tests.cpp.
         */
        private fun isValidUtf8(data: ByteArray): Boolean {
            var pos = 0
            while (pos < data.size) {
                val byte = data[pos].toInt() and 0xFF
                if (byte < 0x80) {
                    pos++
                } else if (byte and 0xE0 == 0xC0) {
                    if (pos + 2 > data.size) return false
                    if (data[pos + 1].toInt() and 0xC0 != 0x80) return false
                    val cp = (byte and 0x1F shl 6) or (data[pos + 1].toInt() and 0x3F)
                    if (cp < 0x80 || cp > 0x7FF) return false
                    pos += 2
                } else if (byte and 0xF0 == 0xE0) {
                    if (pos + 3 > data.size) return false
                    if (data[pos + 1].toInt() and 0xC0 != 0x80) return false
                    if (data[pos + 2].toInt() and 0xC0 != 0x80) return false
                    val cp = (byte and 0x0F shl 12) or
                        (data[pos + 1].toInt() and 0x3F shl 6) or
                        (data[pos + 2].toInt() and 0x3F)
                    if (cp < 0x800 || cp > 0xFFFF || (cp in 0xD800..0xDFFF)) return false
                    pos += 3
                } else if (byte and 0xF8 == 0xF0) {
                    if (pos + 4 > data.size) return false
                    if (data[pos + 1].toInt() and 0xC0 != 0x80) return false
                    if (data[pos + 2].toInt() and 0xC0 != 0x80) return false
                    if (data[pos + 3].toInt() and 0xC0 != 0x80) return false
                    val cp = (byte and 0x07 shl 18) or
                        (data[pos + 1].toInt() and 0x3F shl 12) or
                        (data[pos + 2].toInt() and 0x3F shl 6) or
                        (data[pos + 3].toInt() and 0x3F)
                    if (cp < 0x10000 || cp > 0x10FFFF) return false
                    pos += 4
                } else {
                    return false
                }
            }
            return true
        }

        private fun toHexString(array: ByteArray): String {
            val sb = StringBuilder("[")
            for (i in array.indices) {
                sb.append(String.format("%02X", array[i]))
                if (i < array.size - 1) {
                    sb.append(" ")
                }
            }
            sb.append("]")
            return sb.toString()
        }
    }
}
