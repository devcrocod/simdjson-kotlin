package io.github.devcrocod.simdjson

internal actual fun loadNativeLibrary() {
    NativeLibLoader.ensureLoaded()
}
