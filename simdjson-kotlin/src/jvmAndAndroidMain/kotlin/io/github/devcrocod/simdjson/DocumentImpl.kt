package io.github.devcrocod.simdjson

internal interface DocumentImpl : AutoCloseable {
    fun getObject(): OnDemandObject
    fun getArray(): OnDemandArray
    fun getString(): String
    fun getLong(): Long
    fun getULong(): ULong
    fun getDouble(): Double
    fun getBoolean(): Boolean
    fun isNull(): Boolean
    fun getType(): JsonType
}

internal class JniDocumentImpl(private var docHandle: Long) : DocumentImpl {
    private var consumed = false

    private fun checkConsumed() {
        if (consumed) throw JsonIterationException("Document has already been consumed.")
    }

    private fun isTypeMismatch(e: SimdJsonException): Boolean =
        e is JsonParsingException && e.message?.contains("does not have the requested type") == true

    private fun getTypeViaJni(): JsonType = mapJniValueType(SimdjsonJni.nativeDocGetType(docHandle))

    override fun getObject(): OnDemandObject {
        checkConsumed()
        consumed = true
        try {
            val objHandle = SimdjsonJni.nativeDocGetObject(docHandle)
            return OnDemandObject().also {
                it.impl = JniObjectImpl(objHandle)
            }
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.OBJECT, getTypeViaJni())
            throw e
        }
    }

    override fun getArray(): OnDemandArray {
        checkConsumed()
        consumed = true
        try {
            val arrHandle = SimdjsonJni.nativeDocGetArray(docHandle)
            return OnDemandArray().also {
                it.impl = JniArrayImpl(arrHandle)
            }
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.ARRAY, getTypeViaJni())
            throw e
        }
    }

    override fun getString(): String {
        checkConsumed()
        consumed = true
        try {
            return SimdjsonJni.nativeDocGetString(docHandle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.STRING, getTypeViaJni())
            throw e
        }
    }

    override fun getLong(): Long {
        checkConsumed()
        consumed = true
        try {
            return SimdjsonJni.nativeDocGetInt64(docHandle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.NUMBER, getTypeViaJni())
            throw e
        }
    }

    override fun getULong(): ULong {
        checkConsumed()
        consumed = true
        try {
            return SimdjsonJni.nativeDocGetUint64(docHandle).toULong()
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.NUMBER, getTypeViaJni())
            throw e
        }
    }

    override fun getDouble(): Double {
        checkConsumed()
        consumed = true
        try {
            return SimdjsonJni.nativeDocGetDouble(docHandle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.NUMBER, getTypeViaJni())
            throw e
        }
    }

    override fun getBoolean(): Boolean {
        checkConsumed()
        consumed = true
        try {
            return SimdjsonJni.nativeDocGetBool(docHandle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.BOOLEAN, getTypeViaJni())
            throw e
        }
    }

    override fun isNull(): Boolean {
        checkConsumed()
        val result = SimdjsonJni.nativeDocIsNull(docHandle)
        if (result) consumed = true
        return result
    }

    override fun getType(): JsonType {
        checkConsumed()
        return mapJniValueType(SimdjsonJni.nativeDocGetType(docHandle))
    }

    override fun close() {
        if (docHandle != 0L) {
            SimdjsonJni.nativeDocumentDestroy(docHandle)
            docHandle = 0L
        }
    }
}
