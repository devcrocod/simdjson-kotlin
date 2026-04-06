package io.github.devcrocod.simdjson.serialization

import io.github.devcrocod.simdjson.JsonNull
import io.github.devcrocod.simdjson.JsonObject
import io.github.devcrocod.simdjson.JsonString
import io.github.devcrocod.simdjson.JsonValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

@OptIn(ExperimentalSerializationApi::class)
internal class JsonMapDecoder(
    format: SimdJson,
    jsonObject: JsonObject
) : SimdJsonDecoder(format, jsonObject) {

    private val entries: List<Pair<String, JsonValue>> = jsonObject.toList()
    private val size = entries.size * 2
    private var currentIndex = -1

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        currentIndex++
        return if (currentIndex < size) currentIndex
        else CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = entries.size

    override fun currentValue(): JsonValue {
        val entryIndex = currentIndex / 2
        return if (currentIndex % 2 == 0) {
            JsonString(entries[entryIndex].first)
        } else {
            entries[entryIndex].second
        }
    }

    override fun decodeNotNullMark(): Boolean = currentValue() !is JsonNull
}
