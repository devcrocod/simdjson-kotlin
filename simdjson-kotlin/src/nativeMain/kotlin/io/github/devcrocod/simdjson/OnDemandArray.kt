@file:OptIn(ExperimentalForeignApi::class)

package io.github.devcrocod.simdjson

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import simdjson.*

actual class OnDemandArray internal actual constructor() : Iterable<OnDemandValue>, AutoCloseable {

    private var arrHandle: simdjson_array_t? = null
    private var consumed = false
    private var document: JsonDocument? = null

    internal fun init(arr: simdjson_array_t, doc: JsonDocument? = null) {
        arrHandle = arr
        document = doc
    }

    actual override fun iterator(): Iterator<OnDemandValue> {
        if (consumed) throw JsonIterationException("Array has already been consumed.")
        consumed = true
        val iterHandle = memScoped {
            val out = alloc<simdjson_array_iterator_tVar>()
            checkSimdjsonError(simdjson_array_iterator_create(arrHandle, out.ptr))
            out.value!!
        }
        return NativeArrayIterator(iterHandle)
    }

    actual override fun close() {
        val h = arrHandle ?: return
        document?.unregisterChild(this)
        document = null
        simdjson_array_destroy(h)
        arrHandle = null
    }

    private inner class NativeArrayIterator(
        private var iterHandle: simdjson_array_iterator_t?
    ) : Iterator<OnDemandValue> {

        private var nextValue: OnDemandValue? = null
        private var done = false

        override fun hasNext(): Boolean {
            if (done) return false
            if (nextValue != null) return true
            advance()
            return nextValue != null
        }

        override fun next(): OnDemandValue {
            if (!hasNext()) throw NoSuchElementException("No more elements")
            val v = nextValue!!
            nextValue = null
            return v
        }

        private fun advance() {
            memScoped {
                val hasNextOut = alloc<BooleanVar>()
                val valPtr = alloc<simdjson_value_tVar>()

                checkSimdjsonError(
                    simdjson_array_iterator_next(iterHandle, hasNextOut.ptr, valPtr.ptr)
                )
                if (!hasNextOut.value) {
                    done = true
                    simdjson_array_iterator_destroy(iterHandle)
                    iterHandle = null
                    return
                }
                nextValue = OnDemandValue().also {
                    it.init(valPtr.value!!, document)
                    document?.registerChild(it)
                }
            }
        }
    }
}
