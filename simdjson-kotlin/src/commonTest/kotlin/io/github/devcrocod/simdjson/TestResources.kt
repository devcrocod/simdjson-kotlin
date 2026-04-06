package io.github.devcrocod.simdjson

object TestResources {
    fun load(path: String): ByteArray = platformLoadResource(path)
    fun loadText(path: String): String = load(path).decodeToString()
    fun listFiles(dir: String): List<String> = platformListFiles(dir).sorted()
}

internal expect fun platformLoadResource(path: String): ByteArray
internal expect fun platformListFiles(dir: String): List<String>
