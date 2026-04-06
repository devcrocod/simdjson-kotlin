package io.github.devcrocod.simdjson.serialization

import io.github.devcrocod.simdjson.JsonArray
import io.github.devcrocod.simdjson.JsonNull
import io.github.devcrocod.simdjson.JsonValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

@OptIn(ExperimentalSerializationApi::class)
internal class JsonArrayDecoder(
    format: SimdJson,
    private val jsonArray: JsonArray
) : SimdJsonDecoder(format, jsonArray) {

    private var currentIndex = -1

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        currentIndex++
        return if (currentIndex < jsonArray.size) currentIndex
        else CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = jsonArray.size

    override fun currentValue(): JsonValue =
        if (currentIndex >= 0) jsonArray[currentIndex] else jsonArray

    override fun decodeNotNullMark(): Boolean = jsonArray[currentIndex] !is JsonNull
}
