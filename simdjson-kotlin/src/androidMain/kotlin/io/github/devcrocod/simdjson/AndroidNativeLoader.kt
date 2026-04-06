package io.github.devcrocod.simdjson

internal actual fun loadNativeLibrary() {
    System.loadLibrary("simdjson_jni")
}
