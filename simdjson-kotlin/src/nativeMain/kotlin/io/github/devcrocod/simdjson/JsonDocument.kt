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

actual class JsonDocument internal actual constructor() : AutoCloseable {

    private var docHandle: simdjson_document_t? = null
    private var consumed = false
    private val children = mutableListOf<AutoCloseable>()

    internal fun init(doc: simdjson_document_t) {
        docHandle = doc
    }

    internal fun registerChild(child: AutoCloseable) {
        children.add(child)
    }

    internal fun unregisterChild(child: AutoCloseable) {
        children.remove(child)
    }

    private fun checkConsumed() {
        if (consumed) throw JsonIterationException("Document has already been consumed.")
    }

    private fun getTypeViaApi(): JsonType = memScoped {
        val out = alloc<simdjson_value_typeVar>()
        simdjson_doc_get_type(docHandle, out.ptr)
        mapValueType(out.value)
    }

    actual fun getObject(): OnDemandObject {
        checkConsumed()
        consumed = true
        try {
            return memScoped {
                val objPtr = alloc<simdjson_object_tVar>()
                checkSimdjsonError(simdjson_doc_get_object(docHandle, objPtr.ptr))
                OnDemandObject().also {
                    it.init(objPtr.value!!, this@JsonDocument)
                    registerChild(it)
                }
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.OBJECT, getTypeViaApi())
        }
    }

    actual fun getArray(): OnDemandArray {
        checkConsumed()
        consumed = true
        try {
            return memScoped {
                val arrPtr = alloc<simdjson_array_tVar>()
                checkSimdjsonError(simdjson_doc_get_array(docHandle, arrPtr.ptr))
                OnDemandArray().also {
                    it.init(arrPtr.value!!, this@JsonDocument)
                    registerChild(it)
                }
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.ARRAY, getTypeViaApi())
        }
    }

    actual fun getString(): String {
        checkConsumed()
        consumed = true
        try {
            return extractString { s, l -> simdjson_doc_get_string(docHandle, s, l) }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.STRING, getTypeViaApi())
        }
    }

    actual fun getLong(): Long {
        checkConsumed()
        consumed = true
        try {
            return memScoped {
                val out = alloc<LongVar>()
                checkSimdjsonError(simdjson_doc_get_int64(docHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, getTypeViaApi())
        }
    }

    actual fun getULong(): ULong {
        checkConsumed()
        consumed = true
        try {
            return memScoped {
                val out = alloc<ULongVar>()
                checkSimdjsonError(simdjson_doc_get_uint64(docHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, getTypeViaApi())
        }
    }

    actual fun getDouble(): Double {
        checkConsumed()
        consumed = true
        try {
            return memScoped {
                val out = alloc<DoubleVar>()
                checkSimdjsonError(simdjson_doc_get_double(docHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.NUMBER, getTypeViaApi())
        }
    }

    actual fun getBoolean(): Boolean {
        checkConsumed()
        consumed = true
        try {
            return memScoped {
                val out = alloc<BooleanVar>()
                checkSimdjsonError(simdjson_doc_get_bool(docHandle, out.ptr))
                out.value
            }
        } catch (e: JsonTypeException) {
            throw JsonTypeException(e.message ?: "Type mismatch", JsonType.BOOLEAN, getTypeViaApi())
        }
    }

    actual fun isNull(): Boolean {
        checkConsumed()
        return memScoped {
            val out = alloc<BooleanVar>()
            checkSimdjsonError(simdjson_doc_is_null(docHandle, out.ptr))
            val result = out.value
            if (result) consumed = true
            result
        }
    }

    actual fun getType(): JsonType {
        checkConsumed()
        return memScoped {
            val out = alloc<simdjson_value_typeVar>()
            checkSimdjsonError(simdjson_doc_get_type(docHandle, out.ptr))
            mapValueType(out.value)
        }
    }

    actual override fun close() {
        val h = docHandle ?: return
        val snapshot = children.toList()
        children.clear()
        for (child in snapshot) {
            child.close()
        }
        simdjson_document_destroy(h)
        docHandle = null
    }
}
