package io.github.devcrocod.simdjson

/**
 * Lazy JSON object — forward-only field access.
 */
expect class OnDemandObject internal constructor() : Iterable<OnDemandField>, AutoCloseable {

    /**
     * Find a field by name.
     * Order-sensitive: searches forward from current position.
     * Use for sequential access when field order is known.
     */
    fun findField(name: String): OnDemandValue

    /**
     * Find a field by name, searching in any order.
     * Slower than [findField] — may need to scan from beginning.
     * Use via operator[] syntax.
     */
    operator fun get(name: String): OnDemandValue

    /**
     * Iterate over all fields in order.
     * Consumes the object — can only be called once.
     */
    override fun iterator(): Iterator<OnDemandField>

    override fun close()
}
