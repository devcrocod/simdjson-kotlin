package io.github.devcrocod.simdjson.serialization

import io.github.devcrocod.simdjson.JsonValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encoding.CompositeDecoder

@OptIn(ExperimentalSerializationApi::class)
internal class JsonPrimitiveDecoder(
    format: SimdJson,
    private val primitiveValue: JsonValue
) : SimdJsonDecoder(format, primitiveValue) {

    private var consumed = false

    override fun decodeElementIndex(descriptor: kotlinx.serialization.descriptors.SerialDescriptor): Int {
        return if (!consumed) {
            consumed = true
            0
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun currentValue(): JsonValue = primitiveValue
}
