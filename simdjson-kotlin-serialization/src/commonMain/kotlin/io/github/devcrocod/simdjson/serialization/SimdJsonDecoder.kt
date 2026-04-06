package io.github.devcrocod.simdjson.serialization

import io.github.devcrocod.simdjson.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal sealed class SimdJsonDecoder(
    protected val format: SimdJson,
    protected val value: JsonValue
) : AbstractDecoder() {

    override val serializersModule: SerializersModule
        get() = format.serializersModule

    protected val configuration: SimdJsonConfiguration
        get() = format.configuration

    // --- Primitive decoding ---

    override fun decodeBoolean(): Boolean = when (val v = currentValue()) {
        is JsonBoolean -> v.value
        else -> throw typeMismatch("Boolean", v)
    }

    override fun decodeInt(): Int = when (val v = currentValue()) {
        is JsonNumber -> v.toInt()
        else -> throw typeMismatch("Int", v)
    }

    override fun decodeLong(): Long = when (val v = currentValue()) {
        is JsonNumber -> v.toLong()
        else -> throw typeMismatch("Long", v)
    }

    override fun decodeFloat(): Float = when (val v = currentValue()) {
        is JsonNumber -> v.toDouble().toFloat()
        else -> throw typeMismatch("Float", v)
    }

    override fun decodeDouble(): Double = when (val v = currentValue()) {
        is JsonNumber -> v.toDouble()
        else -> throw typeMismatch("Double", v)
    }

    override fun decodeByte(): Byte = when (val v = currentValue()) {
        is JsonNumber -> {
            val intVal = v.toInt()
            if (intVal < Byte.MIN_VALUE || intVal > Byte.MAX_VALUE)
                throw SimdJsonDecodingException("Value $intVal out of Byte range")
            intVal.toByte()
        }
        else -> throw typeMismatch("Byte", v)
    }

    override fun decodeShort(): Short = when (val v = currentValue()) {
        is JsonNumber -> {
            val intVal = v.toInt()
            if (intVal < Short.MIN_VALUE || intVal > Short.MAX_VALUE)
                throw SimdJsonDecodingException("Value $intVal out of Short range")
            intVal.toShort()
        }
        else -> throw typeMismatch("Short", v)
    }

    override fun decodeChar(): Char = when (val v = currentValue()) {
        is JsonString -> {
            if (v.value.length != 1)
                throw SimdJsonDecodingException("Expected single char, got string '${v.value}'")
            v.value[0]
        }
        else -> throw typeMismatch("Char", v)
    }

    override fun decodeString(): String = when (val v = currentValue()) {
        is JsonString -> v.value
        else -> throw typeMismatch("String", v)
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = when (val v = currentValue()) {
        is JsonString -> {
            val index = enumDescriptor.getElementIndex(v.value)
            if (index == CompositeDecoder.UNKNOWN_NAME)
                throw SimdJsonDecodingException(
                    "Unknown enum value '${v.value}' for ${enumDescriptor.serialName}"
                )
            index
        }
        else -> throw typeMismatch("enum String", v)
    }

    override fun decodeNotNullMark(): Boolean = currentValue() !is JsonNull

    override fun decodeNull(): Nothing? = null

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    // --- Structure dispatch ---

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val current = currentValue()
        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                if (current is JsonObject) {
                    JsonObjectDecoder(format, current)
                } else {
                    // Inline/value class wrapping a primitive
                    JsonPrimitiveDecoder(format, current)
                }
            }

            StructureKind.LIST ->
                JsonArrayDecoder(format, current as? JsonArray
                    ?: throw typeMismatch("Array", current))

            StructureKind.MAP ->
                JsonMapDecoder(format, current as? JsonObject
                    ?: throw typeMismatch("Object (map)", current))

            SerialKind.ENUM -> this

            else -> throw SimdJsonDecodingException(
                "Unsupported structure kind: ${descriptor.kind} for ${descriptor.serialName}"
            )
        }
    }

    // --- Abstract: subclasses define the current value ---
    protected abstract fun currentValue(): JsonValue

    protected fun typeMismatch(expected: String, actual: JsonValue): SimdJsonDecodingException =
        SimdJsonDecodingException("Expected $expected but got ${actual::class.simpleName}")

    companion object {
        fun create(format: SimdJson, value: JsonValue): SimdJsonDecoder = when (value) {
            is JsonObject -> JsonObjectDecoder(format, value)
            is JsonArray -> JsonArrayDecoder(format, value)
            else -> JsonPrimitiveDecoder(format, value)
        }
    }
}
