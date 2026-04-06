package io.github.devcrocod.simdjson

internal class JsonIterator(
    private val indexer: BitIndexes,
    stringBuffer: ByteArray,
    capacity: Int,
    private val maxDepth: Int,
    padding: Int
) {

    private val tapeBuilder = TapeBuilder(capacity, maxDepth, padding, stringBuffer)
    private val isArray = BooleanArray(maxDepth)

    fun walkDocument(buffer: ByteArray, len: Int): JsonValue {
        if (indexer.isEnd()) {
            throw JsonParsingException("No structural element found.")
        }

        tapeBuilder.visitDocumentStart()

        var depth = 0
        var state: Int

        var idx = indexer.getAndAdvance()
        when (buffer[idx]) {
            OPEN_BRACE -> {
                if (buffer[indexer.getLast()] != CLOSE_BRACE) {
                    throw JsonParsingException("Unclosed object. Missing '}' for starting '{'.")
                }
                if (buffer[indexer.peek()] == CLOSE_BRACE) {
                    indexer.advance()
                    tapeBuilder.visitEmptyObject()
                    state = DOCUMENT_END
                } else {
                    state = OBJECT_BEGIN
                }
            }

            OPEN_BRACKET -> {
                if (buffer[indexer.getLast()] != CLOSE_BRACKET) {
                    throw JsonParsingException("Unclosed array. Missing ']' for starting '['.")
                }
                if (buffer[indexer.peek()] == CLOSE_BRACKET) {
                    indexer.advance()
                    tapeBuilder.visitEmptyArray()
                    state = DOCUMENT_END
                } else {
                    state = ARRAY_BEGIN
                }
            }

            else -> {
                tapeBuilder.visitRootPrimitive(buffer, idx, len)
                state = DOCUMENT_END
            }
        }

        while (state != DOCUMENT_END) {
            if (state == OBJECT_BEGIN) {
                if (depth + 1 >= maxDepth) {
                    throw JsonParsingException("Exceeds max depth ($maxDepth)")
                }
                depth++
                isArray[depth] = false
                tapeBuilder.visitObjectStart(depth)

                val keyIdx = indexer.getAndAdvance()
                if (buffer[keyIdx] != QUOTE) {
                    throw JsonParsingException("Object does not start with a key")
                }
                tapeBuilder.incrementCount(depth)
                tapeBuilder.visitKey(buffer, keyIdx)
                state = OBJECT_FIELD
            }

            if (state == OBJECT_FIELD) {
                if (buffer[indexer.getAndAdvance()] != COLON) {
                    throw JsonParsingException("Missing colon after key in object")
                }
                idx = indexer.getAndAdvance()
                when (buffer[idx]) {
                    OPEN_BRACE -> {
                        if (buffer[indexer.peek()] == CLOSE_BRACE) {
                            indexer.advance()
                            tapeBuilder.visitEmptyObject()
                            state = OBJECT_CONTINUE
                        } else {
                            state = OBJECT_BEGIN
                        }
                    }

                    OPEN_BRACKET -> {
                        if (buffer[indexer.peek()] == CLOSE_BRACKET) {
                            indexer.advance()
                            tapeBuilder.visitEmptyArray()
                            state = OBJECT_CONTINUE
                        } else {
                            state = ARRAY_BEGIN
                        }
                    }

                    else -> {
                        tapeBuilder.visitPrimitive(buffer, idx)
                        state = OBJECT_CONTINUE
                    }
                }
            }

            if (state == OBJECT_CONTINUE) {
                when (buffer[indexer.getAndAdvance()]) {
                    COMMA -> {
                        tapeBuilder.incrementCount(depth)
                        val keyIdx = indexer.getAndAdvance()
                        if (buffer[keyIdx] != QUOTE) {
                            throw JsonParsingException("Key string missing at beginning of field in object")
                        }
                        tapeBuilder.visitKey(buffer, keyIdx)
                        state = OBJECT_FIELD
                    }

                    CLOSE_BRACE -> {
                        tapeBuilder.visitObjectEnd(depth)
                        state = SCOPE_END
                    }

                    else -> throw JsonParsingException("No comma between object fields")
                }
            }

            if (state == SCOPE_END) {
                depth--
                if (depth == 0) {
                    state = DOCUMENT_END
                } else if (isArray[depth]) {
                    state = ARRAY_CONTINUE
                } else {
                    state = OBJECT_CONTINUE
                }
            }

            if (state == ARRAY_BEGIN) {
                if (depth + 1 >= maxDepth) {
                    throw JsonParsingException("Exceeds max depth ($maxDepth)")
                }
                depth++
                isArray[depth] = true
                tapeBuilder.visitArrayStart(depth)
                tapeBuilder.incrementCount(depth)
                state = ARRAY_VALUE
            }

            if (state == ARRAY_VALUE) {
                idx = indexer.getAndAdvance()
                when (buffer[idx]) {
                    OPEN_BRACE -> {
                        if (buffer[indexer.peek()] == CLOSE_BRACE) {
                            indexer.advance()
                            tapeBuilder.visitEmptyObject()
                            state = ARRAY_CONTINUE
                        } else {
                            state = OBJECT_BEGIN
                        }
                    }

                    OPEN_BRACKET -> {
                        if (buffer[indexer.peek()] == CLOSE_BRACKET) {
                            indexer.advance()
                            tapeBuilder.visitEmptyArray()
                            state = ARRAY_CONTINUE
                        } else {
                            state = ARRAY_BEGIN
                        }
                    }

                    else -> {
                        tapeBuilder.visitPrimitive(buffer, idx)
                        state = ARRAY_CONTINUE
                    }
                }
            }

            if (state == ARRAY_CONTINUE) {
                when (buffer[indexer.getAndAdvance()]) {
                    COMMA -> {
                        tapeBuilder.incrementCount(depth)
                        state = ARRAY_VALUE
                    }

                    CLOSE_BRACKET -> {
                        tapeBuilder.visitArrayEnd(depth)
                        state = SCOPE_END
                    }

                    else -> throw JsonParsingException("Missing comma between array values")
                }
            }
        }

        tapeBuilder.visitDocumentEnd()

        if (!indexer.isEnd()) {
            throw JsonParsingException("More than one JSON value at the root of the document, or extra characters at the end of the JSON!")
        }

        return tapeBuilder.createJsonValue()
    }

    fun reset() {
        tapeBuilder.reset()
        isArray.fill(false)
    }

    companion object {
        private const val OBJECT_BEGIN = 0
        private const val ARRAY_BEGIN = 1
        private const val DOCUMENT_END = 2
        private const val OBJECT_FIELD = 3
        private const val OBJECT_CONTINUE = 4
        private const val SCOPE_END = 5
        private const val ARRAY_CONTINUE = 6
        private const val ARRAY_VALUE = 7

        private val OPEN_BRACE = '{'.code.toByte()
        private val CLOSE_BRACE = '}'.code.toByte()
        private val OPEN_BRACKET = '['.code.toByte()
        private val CLOSE_BRACKET = ']'.code.toByte()
        private val QUOTE = '"'.code.toByte()
        private val COLON = ':'.code.toByte()
        private val COMMA = ','.code.toByte()
    }
}
