@file:OptIn(ExperimentalForeignApi::class)

package io.github.devcrocod.simdjson

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import simdjson.*

actual class SimdJsonParser actual constructor(
    capacity: Int,
    maxDepth: Int
) : AutoCloseable {

    private var parser: simdjson_parser_t? = simdjson_parser_create(capacity.convert(), maxDepth.convert())
        ?: throw JsonParsingException("Failed to allocate native parser")

    actual fun parse(data: ByteArray, length: Int): JsonValue {
        if (length == 0) {
            throw JsonParsingException("No structural element found.")
        }
        val doc = data.usePinned { pinned ->
            memScoped {
                val docPtr = alloc<simdjson_document_tVar>()
                val offsetOut = alloc<ULongVar>()
                val err = simdjson_dom_parse(
                    parser,
                    pinned.addressOf(0).reinterpret(),
                    length.convert(),
                    docPtr.ptr,
                    offsetOut.ptr
                )
                if (err != SIMDJSON_OK) {
                    val message = simdjson_error_message(err)?.toKString() ?: "Unknown error"
                    val offset = offsetOut.value
                    val kotlinOffset = if (offset == ULong.MAX_VALUE) -1L else offset.toLong()
                    throw when (err) {
                        SIMDJSON_UTF8_ERROR -> JsonEncodingException(message)
                        SIMDJSON_OUT_OF_ORDER_ITERATION,
                        SIMDJSON_SCALAR_DOCUMENT_AS_VALUE -> JsonIterationException(message)
                        else -> JsonParsingException(message, kotlinOffset)
                    }
                }
                docPtr.value!!
            }
        }
        try {
            return materializeDocument(doc)
        } finally {
            simdjson_document_destroy(doc)
        }
    }

    actual fun parse(json: String): JsonValue {
        val bytes = json.encodeToByteArray()
        return parse(bytes, bytes.size)
    }

    actual fun iterate(data: ByteArray, length: Int): JsonDocument {
        if (length == 0) {
            throw JsonParsingException("No structural element found.")
        }
        val doc = data.usePinned { pinned ->
            memScoped {
                val docPtr = alloc<simdjson_document_tVar>()
                val offsetOut = alloc<ULongVar>()
                val err = simdjson_iterate(
                    parser,
                    pinned.addressOf(0).reinterpret(),
                    length.convert(),
                    docPtr.ptr,
                    offsetOut.ptr
                )
                if (err != SIMDJSON_OK) {
                    val message = simdjson_error_message(err)?.toKString() ?: "Unknown error"
                    val offset = offsetOut.value
                    val kotlinOffset = if (offset == ULong.MAX_VALUE) -1L else offset.toLong()
                    throw when (err) {
                        SIMDJSON_UTF8_ERROR -> JsonEncodingException(message)
                        SIMDJSON_OUT_OF_ORDER_ITERATION,
                        SIMDJSON_SCALAR_DOCUMENT_AS_VALUE -> JsonIterationException(message)
                        else -> JsonParsingException(message, kotlinOffset)
                    }
                }
                docPtr.value!!
            }
        }
        return JsonDocument().also { it.init(doc) }
    }

    actual fun iterate(json: String): JsonDocument {
        val bytes = json.encodeToByteArray()
        return iterate(bytes, bytes.size)
    }

    actual override fun close() {
        val p = parser ?: return
        simdjson_parser_destroy(p)
        parser = null
    }

    actual companion object {
        actual val DEFAULT_CAPACITY: Int = 34 * 1024 * 1024
        actual val DEFAULT_MAX_DEPTH: Int = 1024
        actual val SIMDJSON_PADDING: Int = 64
    }
}

// --- DOM / On Demand materialization helpers ---

internal fun materializeDocument(doc: simdjson_document_t): JsonValue = memScoped {
    val typeOut = alloc<simdjson_value_typeVar>()
    checkSimdjsonError(simdjson_doc_get_type(doc, typeOut.ptr))
    when (typeOut.value) {
        SIMDJSON_TYPE_OBJECT -> {
            val objPtr = alloc<simdjson_object_tVar>()
            checkSimdjsonError(simdjson_doc_get_object(doc, objPtr.ptr))
            val obj = objPtr.value!!
            try { materializeObjectEntries(obj) } finally { simdjson_object_destroy(obj) }
        }
        SIMDJSON_TYPE_ARRAY -> {
            val arrPtr = alloc<simdjson_array_tVar>()
            checkSimdjsonError(simdjson_doc_get_array(doc, arrPtr.ptr))
            val arr = arrPtr.value!!
            try { materializeArrayElements(arr) } finally { simdjson_array_destroy(arr) }
        }
        SIMDJSON_TYPE_STRING -> JsonString(extractString { s, l -> simdjson_doc_get_string(doc, s, l) })
        SIMDJSON_TYPE_NUMBER -> {
            val longOut = alloc<LongVar>()
            val err = simdjson_doc_get_int64(doc, longOut.ptr)
            if (err == SIMDJSON_OK) JsonNumber(longValue = longOut.value)
            else {
                val ulongOut = alloc<ULongVar>()
                val err2 = simdjson_doc_get_uint64(doc, ulongOut.ptr)
                if (err2 == SIMDJSON_OK) JsonNumber.ofULong(ulongOut.value)
                else {
                    val dblOut = alloc<DoubleVar>()
                    checkSimdjsonError(simdjson_doc_get_double(doc, dblOut.ptr))
                    JsonNumber(doubleValue = dblOut.value)
                }
            }
        }
        SIMDJSON_TYPE_BOOLEAN -> {
            val out = alloc<BooleanVar>()
            checkSimdjsonError(simdjson_doc_get_bool(doc, out.ptr))
            JsonBoolean(out.value)
        }
        SIMDJSON_TYPE_NULL -> JsonNull
        else -> JsonNull
    }
}

internal fun materializeObjectEntries(obj: simdjson_object_t): JsonObject = memScoped {
    val iterPtr = alloc<simdjson_object_iterator_tVar>()
    checkSimdjsonError(simdjson_object_iterator_create(obj, iterPtr.ptr))
    val iter = iterPtr.value!!
    try {
        val entries = mutableListOf<Pair<String, JsonValue>>()
        val hasNext = alloc<BooleanVar>()
        val keyPtr = alloc<CPointerVar<ByteVar>>()
        val keyLen = alloc<ULongVar>()
        val valPtr = alloc<simdjson_value_tVar>()

        while (true) {
            checkSimdjsonError(
                simdjson_object_iterator_next(iter, hasNext.ptr, keyPtr.ptr, keyLen.ptr, valPtr.ptr)
            )
            if (!hasNext.value) break
            val key = keyPtr.value!!.readBytes(keyLen.value.toInt()).decodeToString()
            val value = valPtr.value!!
            try {
                entries.add(key to materializeValue(value))
            } finally {
                simdjson_value_destroy(value)
            }
        }
        JsonObject(entries)
    } finally {
        simdjson_object_iterator_destroy(iter)
    }
}

internal fun materializeArrayElements(arr: simdjson_array_t): JsonArray = memScoped {
    val iterPtr = alloc<simdjson_array_iterator_tVar>()
    checkSimdjsonError(simdjson_array_iterator_create(arr, iterPtr.ptr))
    val iter = iterPtr.value!!
    try {
        val elements = mutableListOf<JsonValue>()
        val hasNext = alloc<BooleanVar>()
        val valPtr = alloc<simdjson_value_tVar>()

        while (true) {
            checkSimdjsonError(
                simdjson_array_iterator_next(iter, hasNext.ptr, valPtr.ptr)
            )
            if (!hasNext.value) break
            val value = valPtr.value!!
            try {
                elements.add(materializeValue(value))
            } finally {
                simdjson_value_destroy(value)
            }
        }
        JsonArray(elements)
    } finally {
        simdjson_array_iterator_destroy(iter)
    }
}

internal fun materializeValue(value: simdjson_value_t): JsonValue = memScoped {
    val typeOut = alloc<simdjson_value_typeVar>()
    checkSimdjsonError(simdjson_value_get_type(value, typeOut.ptr))
    when (typeOut.value) {
        SIMDJSON_TYPE_OBJECT -> {
            val objPtr = alloc<simdjson_object_tVar>()
            checkSimdjsonError(simdjson_value_get_object(value, objPtr.ptr))
            val obj = objPtr.value!!
            try { materializeObjectEntries(obj) } finally { simdjson_object_destroy(obj) }
        }
        SIMDJSON_TYPE_ARRAY -> {
            val arrPtr = alloc<simdjson_array_tVar>()
            checkSimdjsonError(simdjson_value_get_array(value, arrPtr.ptr))
            val arr = arrPtr.value!!
            try { materializeArrayElements(arr) } finally { simdjson_array_destroy(arr) }
        }
        SIMDJSON_TYPE_STRING -> JsonString(
            extractString { s, l -> simdjson_value_get_string(value, s, l) }
        )
        SIMDJSON_TYPE_NUMBER -> {
            val longOut = alloc<LongVar>()
            val err = simdjson_value_get_int64(value, longOut.ptr)
            if (err == SIMDJSON_OK) JsonNumber(longValue = longOut.value)
            else {
                val ulongOut = alloc<ULongVar>()
                val err2 = simdjson_value_get_uint64(value, ulongOut.ptr)
                if (err2 == SIMDJSON_OK) JsonNumber.ofULong(ulongOut.value)
                else {
                    val dblOut = alloc<DoubleVar>()
                    checkSimdjsonError(simdjson_value_get_double(value, dblOut.ptr))
                    JsonNumber(doubleValue = dblOut.value)
                }
            }
        }
        SIMDJSON_TYPE_BOOLEAN -> {
            val out = alloc<BooleanVar>()
            checkSimdjsonError(simdjson_value_get_bool(value, out.ptr))
            JsonBoolean(out.value)
        }
        SIMDJSON_TYPE_NULL -> JsonNull
        else -> JsonNull
    }
}
