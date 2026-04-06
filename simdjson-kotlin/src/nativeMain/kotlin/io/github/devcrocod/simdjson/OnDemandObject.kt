@file:OptIn(ExperimentalForeignApi::class)

package io.github.devcrocod.simdjson

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import simdjson.*

actual class OnDemandObject internal actual constructor() : Iterable<OnDemandField>, AutoCloseable {

    private var objHandle: simdjson_object_t? = null
    private var consumed = false
    private var document: JsonDocument? = null

    internal fun init(obj: simdjson_object_t, doc: JsonDocument? = null) {
        objHandle = obj
        document = doc
    }

    actual fun findField(name: String): OnDemandValue {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        return memScoped {
            val valPtr = alloc<simdjson_value_tVar>()
            val utf8 = name.encodeToByteArray()
            checkSimdjsonError(
                simdjson_object_find_field(
                    objHandle,
                    name,
                    utf8.size.convert(),
                    valPtr.ptr
                )
            )
            OnDemandValue().also {
                it.init(valPtr.value!!, document)
                document?.registerChild(it)
            }
        }
    }

    actual operator fun get(name: String): OnDemandValue {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        return memScoped {
            val valPtr = alloc<simdjson_value_tVar>()
            val utf8 = name.encodeToByteArray()
            checkSimdjsonError(
                simdjson_object_find_field_unordered(
                    objHandle,
                    name,
                    utf8.size.convert(),
                    valPtr.ptr
                )
            )
            OnDemandValue().also {
                it.init(valPtr.value!!, document)
                document?.registerChild(it)
            }
        }
    }

    actual override fun iterator(): Iterator<OnDemandField> {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        consumed = true
        val iterHandle = memScoped {
            val out = alloc<simdjson_object_iterator_tVar>()
            checkSimdjsonError(simdjson_object_iterator_create(objHandle, out.ptr))
            out.value!!
        }
        return NativeObjectIterator(iterHandle)
    }

    actual override fun close() {
        val h = objHandle ?: return
        document?.unregisterChild(this)
        document = null
        simdjson_object_destroy(h)
        objHandle = null
    }

    private inner class NativeObjectIterator(
        private var iterHandle: simdjson_object_iterator_t?
    ) : Iterator<OnDemandField> {

        private var nextField: OnDemandField? = null
        private var done = false

        override fun hasNext(): Boolean {
            if (done) return false
            if (nextField != null) return true
            advance()
            return nextField != null
        }

        override fun next(): OnDemandField {
            if (!hasNext()) throw NoSuchElementException("No more fields")
            val field = nextField!!
            nextField = null
            return field
        }

        private fun advance() {
            memScoped {
                val hasNextOut = alloc<BooleanVar>()
                val keyPtr = alloc<CPointerVar<ByteVar>>()
                val keyLen = alloc<ULongVar>()
                val valPtr = alloc<simdjson_value_tVar>()

                checkSimdjsonError(
                    simdjson_object_iterator_next(iterHandle, hasNextOut.ptr, keyPtr.ptr, keyLen.ptr, valPtr.ptr)
                )
                if (!hasNextOut.value) {
                    done = true
                    simdjson_object_iterator_destroy(iterHandle)
                    iterHandle = null
                    return
                }
                val key = keyPtr.value!!.readBytes(keyLen.value.toInt()).decodeToString()
                val value = OnDemandValue().also {
                    it.init(valPtr.value!!, document)
                    document?.registerChild(it)
                }
                nextField = OnDemandField().also { it.init(key, value) }
            }
        }
    }
}
