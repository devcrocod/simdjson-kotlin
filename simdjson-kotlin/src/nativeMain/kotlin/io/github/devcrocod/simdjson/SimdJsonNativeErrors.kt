@file:OptIn(ExperimentalForeignApi::class)

package io.github.devcrocod.simdjson

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.value
import simdjson.*

internal fun checkSimdjsonError(code: simdjson_error_code) {
    if (code == SIMDJSON_OK) return
    val message = simdjson_error_message(code)?.toKString() ?: "Unknown error"
    throw when (code) {
        SIMDJSON_UTF8_ERROR -> JsonEncodingException(message)
        SIMDJSON_INCORRECT_TYPE -> JsonTypeException(message, JsonType.NULL, JsonType.NULL)
        SIMDJSON_OUT_OF_ORDER_ITERATION -> JsonIterationException(message)
        SIMDJSON_SCALAR_DOCUMENT_AS_VALUE -> JsonIterationException(message)
        SIMDJSON_NO_SUCH_FIELD -> JsonParsingException(message)
        else -> JsonParsingException(message)
    }
}

internal fun extractString(
    getter: (CPointer<CPointerVar<ByteVar>>, CPointer<ULongVar>) -> simdjson_error_code
): String = memScoped {
    val strPtr = alloc<CPointerVar<ByteVar>>()
    val lenPtr = alloc<ULongVar>()
    checkSimdjsonError(getter(strPtr.ptr, lenPtr.ptr))
    val len = lenPtr.value.toInt()
    if (len == 0) return ""
    strPtr.value!!.readBytes(len).decodeToString()
}

internal fun mapValueType(type: simdjson_value_type): JsonType = when (type) {
    SIMDJSON_TYPE_OBJECT -> JsonType.OBJECT
    SIMDJSON_TYPE_ARRAY -> JsonType.ARRAY
    SIMDJSON_TYPE_STRING -> JsonType.STRING
    SIMDJSON_TYPE_NUMBER -> JsonType.NUMBER
    SIMDJSON_TYPE_BOOLEAN -> JsonType.BOOLEAN
    SIMDJSON_TYPE_NULL -> JsonType.NULL
    else -> JsonType.NULL
}
