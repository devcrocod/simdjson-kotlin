package io.github.devcrocod.simdjson

actual class OnDemandArray internal actual constructor() : Iterable<OnDemandValue>, AutoCloseable {

    internal lateinit var impl: ArrayImpl
    internal var document: JsonDocument? = null

    actual override fun iterator(): Iterator<OnDemandValue> = impl.iterator(document)

    actual override fun close() {
        document?.unregisterChild(this)
        document = null
        impl.close()
    }
}
