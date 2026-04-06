package io.github.devcrocod.simdjson

internal expect fun loadNativeLibrary()

internal object SimdjsonJni {
    init {
        loadNativeLibrary()
    }

    // Parser lifecycle
    external fun nativeParserCreate(capacity: Long, maxDepth: Long): Long
    external fun nativeParserDestroy(parser: Long)

    // On Demand: iterate
    external fun nativeIterate(parser: Long, data: ByteArray, length: Int): Long

    // DOM: parse
    external fun nativeDomParse(parser: Long, data: ByteArray, length: Int): Long

    // Stage 1: structural indexing
    external fun nativeGetStructuralIndices(parser: Long, data: ByteArray, length: Int): IntArray

    // Document
    external fun nativeDocumentDestroy(doc: Long)
    external fun nativeDocGetObject(doc: Long): Long
    external fun nativeDocGetArray(doc: Long): Long
    external fun nativeDocGetString(doc: Long): String
    external fun nativeDocGetInt64(doc: Long): Long
    external fun nativeDocGetUint64(doc: Long): Long
    external fun nativeDocGetDouble(doc: Long): Double
    external fun nativeDocGetBool(doc: Long): Boolean
    external fun nativeDocIsNull(doc: Long): Boolean
    external fun nativeDocGetType(doc: Long): Int

    // Object
    external fun nativeObjectFindField(obj: Long, key: String): Long
    external fun nativeObjectFindFieldUnordered(obj: Long, key: String): Long
    external fun nativeObjectIteratorCreate(obj: Long): Long
    external fun nativeObjectIteratorAdvance(iter: Long): Boolean
    external fun nativeObjectIteratorKey(iter: Long): String
    external fun nativeObjectIteratorValue(iter: Long): Long
    external fun nativeObjectIteratorDestroy(iter: Long)
    external fun nativeObjectDestroy(obj: Long)

    // Array
    external fun nativeArrayIteratorCreate(arr: Long): Long
    external fun nativeArrayIteratorAdvance(iter: Long): Boolean
    external fun nativeArrayIteratorValue(iter: Long): Long
    external fun nativeArrayIteratorDestroy(iter: Long)
    external fun nativeArrayDestroy(arr: Long)

    // Value
    external fun nativeValueGetObject(value: Long): Long
    external fun nativeValueGetArray(value: Long): Long
    external fun nativeValueGetString(value: Long): String
    external fun nativeValueGetInt64(value: Long): Long
    external fun nativeValueGetUint64(value: Long): Long
    external fun nativeValueGetDouble(value: Long): Double
    external fun nativeValueGetBool(value: Long): Boolean
    external fun nativeValueIsNull(value: Long): Boolean
    external fun nativeValueGetType(value: Long): Int
    external fun nativeValueDestroy(value: Long)

    // Error
    external fun nativeErrorMessage(code: Int): String
}
