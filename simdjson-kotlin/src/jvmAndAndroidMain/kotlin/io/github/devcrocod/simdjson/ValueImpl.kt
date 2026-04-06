package io.github.devcrocod.simdjson

internal interface ValueImpl : AutoCloseable {
    fun getObject(): OnDemandObject
    fun getArray(): OnDemandArray
    fun getString(): String
    fun getLong(): Long
    fun getULong(): ULong
    fun getDouble(): Double
    fun getBoolean(): Boolean
    fun isNull(): Boolean
    fun getType(): JsonType
    fun materialize(): JsonValue
    fun ensureConsumed()
}

internal class JniValueImpl(private var handle: Long) : ValueImpl {

    private fun isTypeMismatch(e: SimdJsonException): Boolean =
        e is JsonParsingException && e.message?.contains("does not have the requested type") == true

    override fun getObject(): OnDemandObject {
        try {
            val objHandle = SimdjsonJni.nativeValueGetObject(handle)
            return OnDemandObject().also {
                it.impl = JniObjectImpl(objHandle)
            }
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.OBJECT, getType())
            throw e
        }
    }

    override fun getArray(): OnDemandArray {
        try {
            val arrHandle = SimdjsonJni.nativeValueGetArray(handle)
            return OnDemandArray().also {
                it.impl = JniArrayImpl(arrHandle)
            }
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.ARRAY, getType())
            throw e
        }
    }

    override fun getString(): String {
        try {
            return SimdjsonJni.nativeValueGetString(handle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.STRING, getType())
            throw e
        }
    }

    override fun getLong(): Long {
        try {
            return SimdjsonJni.nativeValueGetInt64(handle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.NUMBER, getType())
            throw e
        }
    }

    override fun getULong(): ULong {
        try {
            return SimdjsonJni.nativeValueGetUint64(handle).toULong()
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.NUMBER, getType())
            throw e
        }
    }

    override fun getDouble(): Double {
        try {
            return SimdjsonJni.nativeValueGetDouble(handle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.NUMBER, getType())
            throw e
        }
    }

    override fun getBoolean(): Boolean {
        try {
            return SimdjsonJni.nativeValueGetBool(handle)
        } catch (e: SimdJsonException) {
            if (isTypeMismatch(e)) throw JsonTypeException(e.message!!, JsonType.BOOLEAN, getType())
            throw e
        }
    }

    override fun isNull(): Boolean = SimdjsonJni.nativeValueIsNull(handle)
    override fun getType(): JsonType = mapJniValueType(SimdjsonJni.nativeValueGetType(handle))
    override fun ensureConsumed() { /* no-op: native side handles state */ }
    override fun materialize(): JsonValue = jniMaterializeValue(handle)

    override fun close() {
        if (handle != 0L) {
            SimdjsonJni.nativeValueDestroy(handle)
            handle = 0L
        }
    }
}
