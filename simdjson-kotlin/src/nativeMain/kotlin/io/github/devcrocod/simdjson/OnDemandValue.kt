@file:OptIn(ExperimentalForeignApi::class)

package io.github.devcrocod.simdjson

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import simdjson.*

actual class OnDemandValue internal actual constructor() : AutoCloseable {

    private var valHandle: simdjson_value_t? = null
    private var accessed = false
    private var document: JsonDocument? = null

    internal fun init(value: simdjson_value_t, doc: JsonDocument? = null) {
        valHandle = value
        document = doc
    }

    private fun checkNotAccessed() {
        if (accessed) throw JsonIterationException("Value has already been consumed.")
    }

    actual fun getObject(): OnDemandObject {
        checkNotAccessed()
        accessed = true
        try {
            return memScoped {
                val objPtr = alloc<simdjson_object_tVar>()
                checkSimdjsonError(simdjson_value_get_object(valHandle, objPtr.ptr))
                OnDemandObject().also {
                    it.init(objPtr.value!!, document)
                    document?.registerChild(it)
                }
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.OBJECT, getType())
        }
    }

    actual fun getArray(): OnDemandArray {
        checkNotAccessed()
        accessed = true
        try {
            return memScoped {
                val arrPtr = alloc<simdjson_array_tVar>()
                checkSimdjsonError(simdjson_value_get_array(valHandle, arrPtr.ptr))
                OnDemandArray().also {
                    it.init(arrPtr.value!!, document)
                    document?.registerChild(it)
                }
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.ARRAY, getType())
        }
    }

    actual fun getString(): String {
        checkNotAccessed()
        accessed = true
        try {
            return extractString { s, l -> simdjson_value_get_string(valHandle, s, l) }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.STRING, getType())
        }
    }

    actual fun getLong(): Long {
        checkNotAccessed()
        accessed = true
        try {
            return memScoped {
                val out = alloc<LongVar>()
                checkSimdjsonError(simdjson_value_get_int64(valHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, getType())
        }
    }

    actual fun getULong(): ULong {
        checkNotAccessed()
        accessed = true
        try {
            return memScoped {
                val out = alloc<ULongVar>()
                checkSimdjsonError(simdjson_value_get_uint64(valHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, getType())
        }
    }

    actual fun getDouble(): Double {
        checkNotAccessed()
        accessed = true
        try {
            return memScoped {
                val out = alloc<DoubleVar>()
                checkSimdjsonError(simdjson_value_get_double(valHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, getType())
        }
    }

    actual fun getBoolean(): Boolean {
        checkNotAccessed()
        accessed = true
        try {
            return memScoped {
                val out = alloc<BooleanVar>()
                checkSimdjsonError(simdjson_value_get_bool(valHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.BOOLEAN, getType())
        }
    }

    actual fun isNull(): Boolean {
        checkNotAccessed()
        return memScoped {
            val out = alloc<BooleanVar>()
            checkSimdjsonError(simdjson_value_is_null(valHandle, out.ptr))
            val result = out.value
            if (result) accessed = true
            result
        }
    }

    actual fun getType(): JsonType = memScoped {
        val out = alloc<simdjson_value_typeVar>()
        checkSimdjsonError(simdjson_value_get_type(valHandle, out.ptr))
        mapValueType(out.value)
    }

    actual fun materialize(): JsonValue {
        checkNotAccessed()
        accessed = true
        return materializeValue(valHandle!!)
    }

    actual override fun close() {
        val h = valHandle ?: return
        document?.unregisterChild(this)
        document = null
        simdjson_value_destroy(h)
        valHandle = null
    }
}
