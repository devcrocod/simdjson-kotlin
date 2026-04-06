package io.github.devcrocod.simdjson

internal class VectorBackend(capacity: Int, maxDepth: Int) : ParserBackend {
    private val bitIndexes = BitIndexes(capacity)
    private val indexer = StructuralIndexer(bitIndexes)
    private val stringBuffer = ByteArray(capacity)
    private val jsonIterator = JsonIterator(bitIndexes, stringBuffer, capacity, maxDepth, SimdJsonParser.SIMDJSON_PADDING)
    private val onDemandIterator = OnDemandJsonIterator(bitIndexes, SimdJsonParser.SIMDJSON_PADDING)
    private val schemaBasedJsonIterator = SchemaBasedJsonIterator(bitIndexes, stringBuffer, SimdJsonParser.SIMDJSON_PADDING)
    private val paddedBuffer = ByteArray(capacity)

    private fun padIfNeeded(buffer: ByteArray, len: Int): ByteArray {
        if (buffer.size - len < SimdJsonParser.SIMDJSON_PADDING) {
            buffer.copyInto(paddedBuffer, 0, 0, len)
            return paddedBuffer
        }
        return buffer
    }

    private fun reset() {
        bitIndexes.reset()
        jsonIterator.reset()
    }

    override fun parse(data: ByteArray, length: Int): JsonValue {
        val padded = padIfNeeded(data, length)
        reset()
        indexer.index(padded, length)
        return jsonIterator.walkDocument(padded, length)
    }

    override fun iterate(data: ByteArray, length: Int): JsonDocument {
        val padded = padIfNeeded(data, length)
        reset()
        indexer.index(padded, length)
        onDemandIterator.init(padded, length)
        return JsonDocument().also {
            it.impl = VectorDocumentImpl(onDemandIterator, stringBuffer)
        }
    }

    override fun <T> parseTyped(data: ByteArray, length: Int, javaClass: Class<T>): T {
        val padded = padIfNeeded(data, length)
        reset()
        indexer.index(padded, length)
        return schemaBasedJsonIterator.walkDocument(padded, length, javaClass)
    }

    override fun close() {}
}
