package io.github.devcrocod.simdjson

actual class OnDemandObject internal actual constructor() : Iterable<OnDemandField>, AutoCloseable {

    internal lateinit var impl: ObjectImpl
    internal var document: JsonDocument? = null

    actual fun findField(name: String): OnDemandValue {
        val value = impl.findField(name)
        value.document = document
        document?.registerChild(value)
        return value
    }

    actual operator fun get(name: String): OnDemandValue {
        val value = impl.get(name)
        value.document = document
        document?.registerChild(value)
        return value
    }

    actual override fun iterator(): Iterator<OnDemandField> = impl.iterator(document)

    actual override fun close() {
        document?.unregisterChild(this)
        document = null
        impl.close()
    }
}
