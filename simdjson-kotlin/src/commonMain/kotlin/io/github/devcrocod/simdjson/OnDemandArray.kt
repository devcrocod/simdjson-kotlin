package io.github.devcrocod.simdjson

/**
 * Lazy JSON array — forward-only element access.
 */
expect class OnDemandArray internal constructor() : Iterable<OnDemandValue>, AutoCloseable {

    /**
     * Iterate over elements.
     * Consumes the array — can only be called once.
     * Supports Kotlin for-in syntax.
     */
    override fun iterator(): Iterator<OnDemandValue>

    override fun close()
}
