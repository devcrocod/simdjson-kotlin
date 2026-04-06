package io.github.devcrocod.simdjson.serialization

import io.github.devcrocod.simdjson.JsonNull
import io.github.devcrocod.simdjson.JsonObject
import io.github.devcrocod.simdjson.JsonValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

@OptIn(ExperimentalSerializationApi::class)
internal class JsonObjectDecoder(
    format: SimdJson,
    private val jsonObject: JsonObject
) : SimdJsonDecoder(format, jsonObject) {

    private var position = 0
    private var currentElementValue: JsonValue? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (position < descriptor.elementsCount) {
            val index = position++
            val name = descriptor.getElementName(index)
            val jsonValue = jsonObject[name]

            if (jsonValue != null) {
                if (configuration.coerceInputValues
                    && jsonValue is JsonNull
                    && !descriptor.getElementDescriptor(index).isNullable
                    && descriptor.isElementOptional(index)
                ) {
                    continue
                }
                currentElementValue = jsonValue
                return index
            }
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun currentValue(): JsonValue =
        currentElementValue ?: jsonObject

    override fun decodeNotNullMark(): Boolean =
        currentElementValue !is JsonNull

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!configuration.ignoreUnknownKeys) {
            val knownKeys = HashSet<String>(descriptor.elementsCount)
            for (i in 0 until descriptor.elementsCount) {
                knownKeys.add(descriptor.getElementName(i))
            }
            for (key in jsonObject.keys()) {
                if (key !in knownKeys) {
                    throw SimdJsonDecodingException(
                        "Encountered unknown key '$key' while deserializing ${descriptor.serialName}. " +
                            "Use 'ignoreUnknownKeys = true' in SimdJson configuration to ignore unknown keys."
                    )
                }
            }
        }
    }
}
