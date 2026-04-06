package io.github.devcrocod.simdjson

actual class OnDemandValue internal actual constructor() : AutoCloseable {

    internal lateinit var impl: ValueImpl
    internal var document: JsonDocument? = null

    private var accessed = false

    private fun checkNotAccessed() {
        if (accessed) throw JsonIterationException("Value has already been consumed.")
    }

    internal fun ensureConsumed() {
        if (!accessed) {
            impl.ensureConsumed()
            accessed = true
        } else {
            impl.ensureConsumed()
        }
    }

    actual fun getObject(): OnDemandObject {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            val obj = impl.getObject()
            obj.document = document
            document?.registerChild(obj)
            return obj
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.OBJECT) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.OBJECT, actualType)
            }
            throw e
        }
    }

    actual fun getArray(): OnDemandArray {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            val arr = impl.getArray()
            arr.document = document
            document?.registerChild(arr)
            return arr
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.ARRAY) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.ARRAY, actualType)
            }
            throw e
        }
    }

    actual fun getString(): String {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            return impl.getString()
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.STRING) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.STRING, actualType)
            }
            throw e
        }
    }

    actual fun getLong(): Long {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            return impl.getLong()
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.NUMBER) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, actualType)
            }
            throw e
        }
    }

    actual fun getULong(): ULong {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            return impl.getULong()
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.NUMBER) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, actualType)
            }
            throw e
        }
    }

    actual fun getDouble(): Double {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            return impl.getDouble()
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.NUMBER) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, actualType)
            }
            throw e
        }
    }

    actual fun getBoolean(): Boolean {
        checkNotAccessed()
        val actualType = try { impl.getType() } catch (_: Exception) { null }
        accessed = true
        try {
            return impl.getBoolean()
        } catch (e: SimdJsonException) {
            if (actualType != null && actualType != JsonType.BOOLEAN) {
                throw JsonTypeException(e.message ?: "Type mismatch", JsonType.BOOLEAN, actualType)
            }
            throw e
        }
    }

    actual fun isNull(): Boolean {
        checkNotAccessed()
        val result = impl.isNull()
        if (result) accessed = true
        return result
    }

    actual fun getType(): JsonType {
        return impl.getType()
    }

    actual fun materialize(): JsonValue {
        checkNotAccessed()
        accessed = true
        return impl.materialize()
    }

    actual override fun close() {
        document?.unregisterChild(this)
        document = null
        impl.close()
    }
}
