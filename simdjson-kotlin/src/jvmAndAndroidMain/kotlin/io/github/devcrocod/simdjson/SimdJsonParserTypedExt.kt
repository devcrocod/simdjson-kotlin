package io.github.devcrocod.simdjson

import kotlin.reflect.KClass

fun <T : Any> SimdJsonParser.parse(data: ByteArray, type: KClass<T>, length: Int = data.size): T =
    backend.parseTyped(data, length, type.java)

inline fun <reified T : Any> SimdJsonParser.parse(data: ByteArray, length: Int = data.size): T =
    parse(data, T::class, length)

fun <T : Any> SimdJsonParser.parse(json: String, type: KClass<T>): T {
    val bytes = json.encodeToByteArray()
    return parse(bytes, type, bytes.size)
}

inline fun <reified T : Any> SimdJsonParser.parse(json: String): T =
    parse(json, T::class)

fun <T : Any> SimdJsonParser.parse(data: ByteArray, length: Int = data.size, javaClass: Class<T>): T =
    backend.parseTyped(data, length, javaClass)
