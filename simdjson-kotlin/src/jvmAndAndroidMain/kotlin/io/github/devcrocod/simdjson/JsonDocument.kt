package io.github.devcrocod.simdjson

actual class JsonDocument internal actual constructor() : AutoCloseable {

    internal lateinit var impl: DocumentImpl
    private val children = mutableListOf<AutoCloseable>()

    internal fun registerChild(child: AutoCloseable) {
        children.add(child)
    }

    internal fun unregisterChild(child: AutoCloseable) {
        children.remove(child)
    }

    actual fun getObject(): OnDemandObject {
        val obj = impl.getObject()
        obj.document = this
        registerChild(obj)
        return obj
    }

    actual fun getArray(): OnDemandArray {
        val arr = impl.getArray()
        arr.document = this
        registerChild(arr)
        return arr
    }

    actual fun getString(): String = impl.getString()
    actual fun getLong(): Long = impl.getLong()
    actual fun getULong(): ULong = impl.getULong()
    actual fun getDouble(): Double = impl.getDouble()
    actual fun getBoolean(): Boolean = impl.getBoolean()
    actual fun isNull(): Boolean = impl.isNull()
    actual fun getType(): JsonType = impl.getType()

    actual override fun close() {
        val snapshot = children.toList()
        children.clear()
        for (child in snapshot) {
            child.close()
        }
        impl.close()
    }
}
