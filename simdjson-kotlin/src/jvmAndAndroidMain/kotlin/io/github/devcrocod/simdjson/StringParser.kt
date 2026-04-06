package io.github.devcrocod.simdjson

internal class StringParser {

    fun parseString(buffer: ByteArray, idx: Int, stringBuffer: ByteArray, stringBufferIdx: Int): Int {
        val dst = doParseString(buffer, idx, stringBuffer, stringBufferIdx + Int.SIZE_BYTES)
        val len = dst - stringBufferIdx - Int.SIZE_BYTES
        IntegerUtils.toBytes(len, stringBuffer, stringBufferIdx)
        return dst
    }

    fun parseString(buffer: ByteArray, idx: Int, stringBuffer: ByteArray): Int {
        return doParseString(buffer, idx, stringBuffer, 0)
    }

    private fun doParseString(buffer: ByteArray, idx: Int, stringBuffer: ByteArray, offset: Int): Int {
        var src = idx + 1
        var dst = offset
        while (true) {
            val b = buffer[src]
            when {
                b == QUOTE -> break
                b == BACKSLASH -> {
                    val escapeChar = buffer[src + 1]
                    if (escapeChar == 'u'.code.toByte()) {
                        var codePoint = CharacterUtils.hexToInt(buffer, src + 2)
                        src += 6
                        if (codePoint in MIN_HIGH_SURROGATE..MAX_HIGH_SURROGATE) {
                            codePoint = parseLowSurrogate(buffer, src, codePoint)
                            src += 6
                        } else if (codePoint in MIN_LOW_SURROGATE..MAX_LOW_SURROGATE) {
                            throw JsonParsingException("Invalid code point. The range U+DC00\u2013U+DFFF is reserved for low surrogate.")
                        }
                        dst += storeCodePointInStringBuffer(codePoint, dst, stringBuffer)
                    } else {
                        stringBuffer[dst] = CharacterUtils.escape(escapeChar)
                        src += 2
                        dst++
                    }
                }
                else -> {
                    stringBuffer[dst] = b
                    src++
                    dst++
                }
            }
        }
        return dst
    }

    fun parseChar(buffer: ByteArray, startIdx: Int): Char {
        var idx = startIdx + 1
        val character: Char
        if (buffer[idx] == '\\'.code.toByte()) {
            val escapeChar = buffer[idx + 1]
            if (escapeChar == 'u'.code.toByte()) {
                val codePoint = CharacterUtils.hexToInt(buffer, idx + 2)
                if (codePoint in MIN_HIGH_SURROGATE..MAX_LOW_SURROGATE) {
                    throw JsonParsingException("Invalid code point. Should be within the range U+0000\u2013U+D777 or U+E000\u2013U+FFFF.")
                }
                if (codePoint < 0) {
                    throw JsonParsingException("Invalid unicode escape sequence.")
                }
                character = codePoint.toChar()
                idx += 6
            } else {
                character = CharacterUtils.escape(escapeChar).toInt().toChar()
                idx += 2
            }
        } else if (buffer[idx] >= 0) {
            character = buffer[idx].toInt().toChar()
            idx++
        } else if ((buffer[idx].toInt() and 0b11100000) == 0b11000000) {
            val codePoint = (buffer[idx].toInt() and 0b00011111) shl 6 or (buffer[idx + 1].toInt() and 0b00111111)
            character = codePoint.toChar()
            idx += 2
        } else if ((buffer[idx].toInt() and 0b11110000) == 0b11100000) {
            val codePoint = (buffer[idx].toInt() and 0b00001111) shl 12 or
                ((buffer[idx + 1].toInt() and 0b00111111) shl 6) or
                (buffer[idx + 2].toInt() and 0b00111111)
            character = codePoint.toChar()
            idx += 3
        } else {
            throw JsonParsingException("String cannot be deserialized to a char. Expected a single 16-bit code unit character.")
        }
        if (buffer[idx] != '"'.code.toByte()) {
            throw JsonParsingException("String cannot be deserialized to a char. Expected a single-character string.")
        }
        return character
    }

    private fun parseLowSurrogate(buffer: ByteArray, src: Int, codePoint: Int): Int {
        if ((buffer[src].toInt() shl 8 or (buffer[src + 1].toInt() and 0xFF)) != ('\\'.code shl 8 or 'u'.code)) {
            throw JsonParsingException("Low surrogate should start with '\\u'")
        } else {
            val codePoint2 = CharacterUtils.hexToInt(buffer, src + 2)
            val lowBit = codePoint2 - MIN_LOW_SURROGATE
            if (lowBit shr 10 == 0) {
                return (((codePoint - MIN_HIGH_SURROGATE) shl 10) or lowBit) + 0x10000
            } else {
                throw JsonParsingException("Invalid code point. Low surrogate should be in the range U+DC00\u2013U+DFFF.")
            }
        }
    }

    private fun storeCodePointInStringBuffer(codePoint: Int, dst: Int, stringBuffer: ByteArray): Int {
        if (codePoint < 0) {
            throw JsonParsingException("Invalid unicode escape sequence.")
        }
        if (codePoint <= 0x7F) {
            stringBuffer[dst] = codePoint.toByte()
            return 1
        }
        if (codePoint <= 0x7FF) {
            stringBuffer[dst] = ((codePoint shr 6) + 192).toByte()
            stringBuffer[dst + 1] = ((codePoint and 63) + 128).toByte()
            return 2
        }
        if (codePoint <= 0xFFFF) {
            stringBuffer[dst] = ((codePoint shr 12) + 224).toByte()
            stringBuffer[dst + 1] = (((codePoint shr 6) and 63) + 128).toByte()
            stringBuffer[dst + 2] = ((codePoint and 63) + 128).toByte()
            return 3
        }
        if (codePoint <= 0x10FFFF) {
            stringBuffer[dst] = ((codePoint shr 18) + 240).toByte()
            stringBuffer[dst + 1] = (((codePoint shr 12) and 63) + 128).toByte()
            stringBuffer[dst + 2] = (((codePoint shr 6) and 63) + 128).toByte()
            stringBuffer[dst + 3] = ((codePoint and 63) + 128).toByte()
            return 4
        }
        throw IllegalStateException("Code point is greater than 0x110000.")
    }

    companion object {
        private val BACKSLASH = '\\'.code.toByte()
        private val QUOTE = '"'.code.toByte()
        private const val MIN_HIGH_SURROGATE = 0xD800
        private const val MAX_HIGH_SURROGATE = 0xDBFF
        private const val MIN_LOW_SURROGATE = 0xDC00
        private const val MAX_LOW_SURROGATE = 0xDFFF
    }
}
