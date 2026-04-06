package io.github.devcrocod.simdjson

internal class VectorArrayImpl(
    private val iter: OnDemandJsonIterator,
    private val stringBuffer: ByteArray,
    private val result: OnDemandJsonIterator.IteratorResult
) : ArrayImpl {
    private var consumed = (result == OnDemandJsonIterator.IteratorResult.NULL)

    override fun iterator(document: JsonDocument?): Iterator<OnDemandValue> {
        if (consumed) throw JsonIterationException("Array has already been consumed.")
        consumed = true
        return VectorArrayElementIterator(document)
    }

    override fun close() {}

    private inner class VectorArrayElementIterator(
        private val document: JsonDocument?
    ) : Iterator<OnDemandValue> {
        private var state = if (result == OnDemandJsonIterator.IteratorResult.NOT_EMPTY) ITER_FIRST else ITER_DONE
        private var prev: OnDemandValue? = null

        override fun hasNext(): Boolean = when (state) {
            ITER_FIRST -> true
            ITER_NEED_CHECK -> {
                prev?.ensureConsumed()
                val more = iter.nextArrayElement()
                state = if (more) ITER_HAS_MORE else ITER_DONE
                more
            }
            ITER_HAS_MORE -> true
            ITER_DONE -> false
            else -> false
        }

        override fun next(): OnDemandValue {
            if (!hasNext()) throw NoSuchElementException()
            state = ITER_NEED_CHECK
            val value = OnDemandValue().also {
                it.impl = VectorValueImpl(iter, stringBuffer)
                it.document = document
                document?.registerChild(it)
            }
            prev = value
            return value
        }
    }
}

private const val ITER_FIRST = 0
private const val ITER_NEED_CHECK = 1
private const val ITER_HAS_MORE = 2
private const val ITER_DONE = 3
