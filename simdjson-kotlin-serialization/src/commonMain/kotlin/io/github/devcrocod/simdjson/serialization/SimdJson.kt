package io.github.devcrocod.simdjson.serialization

import io.github.devcrocod.simdjson.JsonValue
import io.github.devcrocod.simdjson.SimdJsonParser
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.SerializersModule

class SimdJson internal constructor(
    override val serializersModule: SerializersModule,
    val configuration: SimdJsonConfiguration
) : StringFormat {

    override fun <T> decodeFromString(
        deserializer: DeserializationStrategy<T>,
        string: String
    ): T {
        val dom = SimdJsonParser().use { it.parse(string) }
        return decodeFromJsonValue(deserializer, dom)
    }

    fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>,
        bytes: ByteArray,
        length: Int = bytes.size
    ): T {
        val dom = SimdJsonParser().use { it.parse(bytes, length) }
        return decodeFromJsonValue(deserializer, dom)
    }

    override fun <T> encodeToString(
        serializer: SerializationStrategy<T>,
        value: T
    ): String = throw UnsupportedOperationException(
        "simdjson-kotlin is a parser-only library. Use kotlinx.serialization.json for encoding."
    )

    private fun <T> decodeFromJsonValue(
        deserializer: DeserializationStrategy<T>,
        value: JsonValue
    ): T {
        val decoder = SimdJsonDecoder.create(this, value)
        return decoder.decodeSerializableValue(deserializer)
    }
}

fun SimdJson(
    from: SimdJson = SimdJson(SerializersModule { }, SimdJsonConfiguration()),
    builderAction: SimdJsonBuilder.() -> Unit
): SimdJson {
    val builder = SimdJsonBuilder(from)
    builder.builderAction()
    return builder.build()
}

class SimdJsonBuilder internal constructor(from: SimdJson) {
    var ignoreUnknownKeys: Boolean = from.configuration.ignoreUnknownKeys
    var isLenient: Boolean = from.configuration.isLenient
    var coerceInputValues: Boolean = from.configuration.coerceInputValues
    var serializersModule: SerializersModule = from.serializersModule

    fun build(): SimdJson = SimdJson(
        serializersModule = serializersModule,
        configuration = SimdJsonConfiguration(
            ignoreUnknownKeys = ignoreUnknownKeys,
            isLenient = isLenient,
            coerceInputValues = coerceInputValues
        )
    )
}
