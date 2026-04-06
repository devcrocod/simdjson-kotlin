package io.github.devcrocod.simdjson

internal interface ObjectImpl : AutoCloseable {
    fun findField(name: String): OnDemandValue
    fun get(name: String): OnDemandValue
    fun iterator(document: JsonDocument?): Iterator<OnDemandField>
}

internal class JniObjectImpl(private var objHandle: Long) : ObjectImpl {
    private var consumed = false

    override fun findField(name: String): OnDemandValue {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        val valHandle = SimdjsonJni.nativeObjectFindField(objHandle, name)
        return OnDemandValue().also {
            it.impl = JniValueImpl(valHandle)
        }
    }

    override fun get(name: String): OnDemandValue {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        val valHandle = SimdjsonJni.nativeObjectFindFieldUnordered(objHandle, name)
        return OnDemandValue().also {
            it.impl = JniValueImpl(valHandle)
        }
    }

    override fun iterator(document: JsonDocument?): Iterator<OnDemandField> {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        consumed = true
        val iterHandle = SimdjsonJni.nativeObjectIteratorCreate(objHandle)
        return JniObjectIterator(iterHandle, document)
    }

    override fun close() {
        if (objHandle != 0L) {
            SimdjsonJni.nativeObjectDestroy(objHandle)
            objHandle = 0L
        }
    }
}

private class JniObjectIterator(
    private var iterHandle: Long,
    private val document: JsonDocument?
) : Iterator<OnDemandField> {
    private var nextField: OnDemandField? = null
    private var done = false

    override fun hasNext(): Boolean {
        if (done) return false
        if (nextField != null) return true
        advance()
        return nextField != null
    }

    override fun next(): OnDemandField {
        if (!hasNext()) throw NoSuchElementException("No more fields")
        val field = nextField!!
        nextField = null
        return field
    }

    private fun advance() {
        val hasNext = SimdjsonJni.nativeObjectIteratorAdvance(iterHandle)
        if (!hasNext) {
            done = true
            SimdjsonJni.nativeObjectIteratorDestroy(iterHandle)
            iterHandle = 0L
            return
        }
        val key = SimdjsonJni.nativeObjectIteratorKey(iterHandle)
        val valHandle = SimdjsonJni.nativeObjectIteratorValue(iterHandle)
        val value = OnDemandValue().also {
            it.impl = JniValueImpl(valHandle)
            it.document = document
            document?.registerChild(it)
        }
        nextField = OnDemandField().also { it.init(key, value) }
    }
}
