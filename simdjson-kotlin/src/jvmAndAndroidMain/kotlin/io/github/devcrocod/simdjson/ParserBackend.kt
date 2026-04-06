package io.github.devcrocod.simdjson

internal interface ParserBackend : AutoCloseable {
    fun parse(data: ByteArray, length: Int): JsonValue
    fun iterate(data: ByteArray, length: Int): JsonDocument
    fun <T> parseTyped(data: ByteArray, length: Int, javaClass: Class<T>): T
}

internal class JniBackend(capacity: Int, maxDepth: Int) : ParserBackend {
    private var parserHandle: Long = SimdjsonJni.nativeParserCreate(capacity.toLong(), maxDepth.toLong())
    private val bitIndexes = BitIndexes(capacity)
    private val stringBuffer = ByteArray(capacity)
    private val schemaBasedJsonIterator = SchemaBasedJsonIterator(bitIndexes, stringBuffer, SimdJsonParser.SIMDJSON_PADDING)
    private val paddedBuffer = ByteArray(capacity)

    override fun parse(data: ByteArray, length: Int): JsonValue {
        val docHandle = SimdjsonJni.nativeDomParse(parserHandle, data, length)
        try {
            return jniMaterializeDocument(docHandle)
        } finally {
            SimdjsonJni.nativeDocumentDestroy(docHandle)
        }
    }

    override fun iterate(data: ByteArray, length: Int): JsonDocument {
        val docHandle = SimdjsonJni.nativeIterate(parserHandle, data, length)
        return JsonDocument().also {
            it.impl = JniDocumentImpl(docHandle)
        }
    }

    override fun <T> parseTyped(data: ByteArray, length: Int, javaClass: Class<T>): T {
        val indices = SimdjsonJni.nativeGetStructuralIndices(parserHandle, data, length)
        bitIndexes.loadFromArray(indices, indices.size)
        val padded = padIfNeeded(data, length)
        return schemaBasedJsonIterator.walkDocument(padded, length, javaClass)
    }

    private fun padIfNeeded(buffer: ByteArray, len: Int): ByteArray {
        if (buffer.size - len < SimdJsonParser.SIMDJSON_PADDING) {
            buffer.copyInto(paddedBuffer, 0, 0, len)
            return paddedBuffer
        }
        return buffer
    }

    override fun close() {
        if (parserHandle != 0L) {
            SimdjsonJni.nativeParserDestroy(parserHandle)
            parserHandle = 0L
        }
    }
}
