package io.github.devcrocod.simdjson

internal fun jniMaterializeDocument(docHandle: Long): JsonValue {
    val type = SimdjsonJni.nativeDocGetType(docHandle)
    return when (type) {
        JNI_TYPE_OBJECT -> {
            val objHandle = SimdjsonJni.nativeDocGetObject(docHandle)
            try {
                jniMaterializeObjectEntries(objHandle)
            } finally {
                SimdjsonJni.nativeObjectDestroy(objHandle)
            }
        }
        JNI_TYPE_ARRAY -> {
            val arrHandle = SimdjsonJni.nativeDocGetArray(docHandle)
            try {
                jniMaterializeArrayElements(arrHandle)
            } finally {
                SimdjsonJni.nativeArrayDestroy(arrHandle)
            }
        }
        JNI_TYPE_STRING -> JsonString(SimdjsonJni.nativeDocGetString(docHandle))
        JNI_TYPE_NUMBER -> {
            try {
                JsonNumber(longValue = SimdjsonJni.nativeDocGetInt64(docHandle))
            } catch (_: SimdJsonException) {
                try {
                    JsonNumber.ofULong(SimdjsonJni.nativeDocGetUint64(docHandle).toULong())
                } catch (_: SimdJsonException) {
                    JsonNumber(doubleValue = SimdjsonJni.nativeDocGetDouble(docHandle))
                }
            }
        }
        JNI_TYPE_BOOLEAN -> JsonBoolean(SimdjsonJni.nativeDocGetBool(docHandle))
        JNI_TYPE_NULL -> JsonNull
        else -> JsonNull
    }
}

internal fun jniMaterializeObjectEntries(objHandle: Long): JsonObject {
    val iterHandle = SimdjsonJni.nativeObjectIteratorCreate(objHandle)
    try {
        val entries = mutableListOf<Pair<String, JsonValue>>()
        while (SimdjsonJni.nativeObjectIteratorAdvance(iterHandle)) {
            val key = SimdjsonJni.nativeObjectIteratorKey(iterHandle)
            val valHandle = SimdjsonJni.nativeObjectIteratorValue(iterHandle)
            try {
                entries.add(key to jniMaterializeValue(valHandle))
            } finally {
                SimdjsonJni.nativeValueDestroy(valHandle)
            }
        }
        return JsonObject(entries)
    } finally {
        SimdjsonJni.nativeObjectIteratorDestroy(iterHandle)
    }
}

internal fun jniMaterializeArrayElements(arrHandle: Long): JsonArray {
    val iterHandle = SimdjsonJni.nativeArrayIteratorCreate(arrHandle)
    try {
        val elements = mutableListOf<JsonValue>()
        while (SimdjsonJni.nativeArrayIteratorAdvance(iterHandle)) {
            val valHandle = SimdjsonJni.nativeArrayIteratorValue(iterHandle)
            try {
                elements.add(jniMaterializeValue(valHandle))
            } finally {
                SimdjsonJni.nativeValueDestroy(valHandle)
            }
        }
        return JsonArray(elements)
    } finally {
        SimdjsonJni.nativeArrayIteratorDestroy(iterHandle)
    }
}

internal fun jniMaterializeValue(valHandle: Long): JsonValue {
    val type = SimdjsonJni.nativeValueGetType(valHandle)
    return when (type) {
        JNI_TYPE_OBJECT -> {
            val objHandle = SimdjsonJni.nativeValueGetObject(valHandle)
            try {
                jniMaterializeObjectEntries(objHandle)
            } finally {
                SimdjsonJni.nativeObjectDestroy(objHandle)
            }
        }
        JNI_TYPE_ARRAY -> {
            val arrHandle = SimdjsonJni.nativeValueGetArray(valHandle)
            try {
                jniMaterializeArrayElements(arrHandle)
            } finally {
                SimdjsonJni.nativeArrayDestroy(arrHandle)
            }
        }
        JNI_TYPE_STRING -> JsonString(SimdjsonJni.nativeValueGetString(valHandle))
        JNI_TYPE_NUMBER -> {
            try {
                JsonNumber(longValue = SimdjsonJni.nativeValueGetInt64(valHandle))
            } catch (_: SimdJsonException) {
                try {
                    JsonNumber.ofULong(SimdjsonJni.nativeValueGetUint64(valHandle).toULong())
                } catch (_: SimdJsonException) {
                    JsonNumber(doubleValue = SimdjsonJni.nativeValueGetDouble(valHandle))
                }
            }
        }
        JNI_TYPE_BOOLEAN -> JsonBoolean(SimdjsonJni.nativeValueGetBool(valHandle))
        JNI_TYPE_NULL -> JsonNull
        else -> JsonNull
    }
}
