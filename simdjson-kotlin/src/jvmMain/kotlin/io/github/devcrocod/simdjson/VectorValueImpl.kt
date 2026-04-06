package io.github.devcrocod.simdjson

internal class VectorValueImpl(
    private val iter: OnDemandJsonIterator,
    private val stringBuffer: ByteArray
) : ValueImpl {
    private val parentDepth = iter.getDepth() - 1

    override fun getObject(): OnDemandObject {
        val result = iter.startIteratingObject()
        return OnDemandObject().also {
            it.impl = VectorObjectImpl(iter, stringBuffer, result)
        }
    }

    override fun getArray(): OnDemandArray {
        val result = iter.startIteratingArray()
        return OnDemandArray().also {
            it.impl = VectorArrayImpl(iter, stringBuffer, result)
        }
    }

    override fun getString(): String {
        val len = iter.getString(stringBuffer)
        if (len == -1) throw JsonTypeException("Expected string, got null", JsonType.STRING, JsonType.NULL)
        return String(stringBuffer, 0, len, Charsets.UTF_8)
    }

    override fun getLong(): Long = iter.getLong()
    override fun getULong(): ULong = iter.getULong()
    override fun getDouble(): Double = iter.getDouble()
    override fun getBoolean(): Boolean = iter.getBoolean()

    override fun isNull(): Boolean = iter.isNull()

    override fun getType(): JsonType = iter.peekType()

    override fun ensureConsumed() {
        iter.skipChild(parentDepth)
    }

    override fun materialize(): JsonValue = materializeInternal()

    override fun close() {}

    private fun materializeInternal(): JsonValue {
        return when (iter.peekType()) {
            JsonType.OBJECT -> materializeObject()
            JsonType.ARRAY -> materializeArray()
            JsonType.STRING -> {
                val len = iter.getString(stringBuffer)
                if (len == -1) JsonNull
                else JsonString(String(stringBuffer, 0, len, Charsets.UTF_8))
            }
            JsonType.NUMBER -> {
                if (iter.peekNumberIsFloat()) {
                    JsonNumber(doubleValue = iter.getDouble())
                } else if (iter.peekNumberIsInLongRange()) {
                    JsonNumber(longValue = iter.getLong())
                } else {
                    JsonNumber.ofULong(iter.getULong())
                }
            }
            JsonType.BOOLEAN -> JsonBoolean(iter.getBoolean())
            JsonType.NULL -> {
                iter.isNull()
                JsonNull
            }
        }
    }

    private fun materializeObject(): JsonValue {
        val result = iter.startIteratingObject()
        if (result == OnDemandJsonIterator.IteratorResult.NULL) return JsonNull
        if (result == OnDemandJsonIterator.IteratorResult.EMPTY) return JsonObject(emptyList())

        val entries = mutableListOf<Pair<String, JsonValue>>()
        var hasMore = true
        while (hasMore) {
            val nameLen = iter.getFieldName(stringBuffer)
            val name = String(stringBuffer, 0, nameLen, Charsets.UTF_8)
            iter.moveToFieldValue()
            val childValue = materializeChild()
            entries.add(name to childValue)
            hasMore = iter.nextObjectField()
        }
        return JsonObject(entries)
    }

    private fun materializeArray(): JsonValue {
        val result = iter.startIteratingArray()
        if (result == OnDemandJsonIterator.IteratorResult.NULL) return JsonNull
        if (result == OnDemandJsonIterator.IteratorResult.EMPTY) return JsonArray(emptyList())

        val elements = mutableListOf<JsonValue>()
        var hasMore = true
        while (hasMore) {
            elements.add(materializeChild())
            hasMore = iter.nextArrayElement()
        }
        return JsonArray(elements)
    }

    private fun materializeChild(): JsonValue {
        return when (iter.peekType()) {
            JsonType.OBJECT -> materializeObject()
            JsonType.ARRAY -> materializeArray()
            JsonType.STRING -> {
                val len = iter.getString(stringBuffer)
                if (len == -1) JsonNull
                else JsonString(String(stringBuffer, 0, len, Charsets.UTF_8))
            }
            JsonType.NUMBER -> {
                if (iter.peekNumberIsFloat()) {
                    JsonNumber(doubleValue = iter.getDouble())
                } else if (iter.peekNumberIsInLongRange()) {
                    JsonNumber(longValue = iter.getLong())
                } else {
                    JsonNumber.ofULong(iter.getULong())
                }
            }
            JsonType.BOOLEAN -> JsonBoolean(iter.getBoolean())
            JsonType.NULL -> {
                iter.isNull()
                JsonNull
            }
        }
    }
}
