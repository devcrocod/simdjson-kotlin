package io.github.devcrocod.simdjson

internal interface ArrayImpl : AutoCloseable {
    fun iterator(document: JsonDocument?): Iterator<OnDemandValue>
}

internal class JniArrayImpl(private var arrHandle: Long) : ArrayImpl {
    private var consumed = false

    override fun iterator(document: JsonDocument?): Iterator<OnDemandValue> {
        if (consumed) throw JsonIterationException("Array has already been consumed.")
        consumed = true
        val iterHandle = SimdjsonJni.nativeArrayIteratorCreate(arrHandle)
        return JniArrayIterator(iterHandle, document)
    }

    override fun close() {
        if (arrHandle != 0L) {
            SimdjsonJni.nativeArrayDestroy(arrHandle)
            arrHandle = 0L
        }
    }
}

private class JniArrayIterator(
    private var iterHandle: Long,
    private val document: JsonDocument?
) : Iterator<OnDemandValue> {
    private var nextValue: OnDemandValue? = null
    private var done = false

    override fun hasNext(): Boolean {
        if (done) return false
        if (nextValue != null) return true
        advance()
        return nextValue != null
    }

    override fun next(): OnDemandValue {
        if (!hasNext()) throw NoSuchElementException("No more elements")
        val v = nextValue!!
        nextValue = null
        return v
    }

    private fun advance() {
        val hasNext = SimdjsonJni.nativeArrayIteratorAdvance(iterHandle)
        if (!hasNext) {
            done = true
            SimdjsonJni.nativeArrayIteratorDestroy(iterHandle)
            iterHandle = 0L
            return
        }
        val valHandle = SimdjsonJni.nativeArrayIteratorValue(iterHandle)
        nextValue = OnDemandValue().also {
            it.impl = JniValueImpl(valHandle)
            it.document = document
            document?.registerChild(it)
        }
    }
}
