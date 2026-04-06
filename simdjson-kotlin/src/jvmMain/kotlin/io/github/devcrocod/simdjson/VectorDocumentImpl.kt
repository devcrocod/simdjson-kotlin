package io.github.devcrocod.simdjson

internal class VectorDocumentImpl(
    private val iterator: OnDemandJsonIterator,
    private val stringBuffer: ByteArray
) : DocumentImpl {
    private var consumed = false

    private fun checkNotConsumed() {
        if (consumed) throw JsonIterationException("Document value has already been consumed.")
    }

    override fun getObject(): OnDemandObject {
        checkNotConsumed()
        consumed = true
        val result = iterator.startIteratingRootObject()
        return OnDemandObject().also {
            it.impl = VectorObjectImpl(iterator, stringBuffer, result)
        }
    }

    override fun getArray(): OnDemandArray {
        checkNotConsumed()
        consumed = true
        val result = iterator.startIteratingRootArray()
        return OnDemandArray().also {
            it.impl = VectorArrayImpl(iterator, stringBuffer, result)
        }
    }

    override fun getString(): String {
        checkNotConsumed()
        consumed = true
        val len = iterator.getRootString(stringBuffer)
        if (len == -1) throw JsonTypeException("Expected string, got null", JsonType.STRING, JsonType.NULL)
        return String(stringBuffer, 0, len, Charsets.UTF_8)
    }

    override fun getLong(): Long {
        checkNotConsumed()
        consumed = true
        return iterator.getRootLong()
    }

    override fun getULong(): ULong {
        checkNotConsumed()
        consumed = true
        return iterator.getRootULong()
    }

    override fun getDouble(): Double {
        checkNotConsumed()
        consumed = true
        return iterator.getRootDouble()
    }

    override fun getBoolean(): Boolean {
        checkNotConsumed()
        consumed = true
        return iterator.getRootBoolean()
    }

    override fun isNull(): Boolean {
        checkNotConsumed()
        val result = iterator.isRootNull()
        if (result) consumed = true
        return result
    }

    override fun getType(): JsonType {
        checkNotConsumed()
        return iterator.peekType()
    }

    override fun close() {}
}
