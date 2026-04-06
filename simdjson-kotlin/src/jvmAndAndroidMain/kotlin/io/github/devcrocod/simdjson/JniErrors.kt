package io.github.devcrocod.simdjson

// Value type constants matching simdjson_value_type enum in simdjson_c.h
internal const val JNI_TYPE_OBJECT = 1
internal const val JNI_TYPE_ARRAY = 2
internal const val JNI_TYPE_STRING = 3
internal const val JNI_TYPE_NUMBER = 4
internal const val JNI_TYPE_BOOLEAN = 5
internal const val JNI_TYPE_NULL = 6

internal fun mapJniValueType(type: Int): JsonType = when (type) {
    JNI_TYPE_OBJECT -> JsonType.OBJECT
    JNI_TYPE_ARRAY -> JsonType.ARRAY
    JNI_TYPE_STRING -> JsonType.STRING
    JNI_TYPE_NUMBER -> JsonType.NUMBER
    JNI_TYPE_BOOLEAN -> JsonType.BOOLEAN
    JNI_TYPE_NULL -> JsonType.NULL
    else -> JsonType.NULL
}
