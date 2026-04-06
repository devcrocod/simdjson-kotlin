package io.github.devcrocod.simdjson

/**
 * Lazy JSON document for forward-only iteration.
 * Lifetime tied to the parser that created it.
 * Invalid after next parse/iterate call on the same parser.
 */
expect class JsonDocument internal constructor() : AutoCloseable {

    /** Get the root value as an object. */
    fun getObject(): OnDemandObject

    /** Get the root value as an array. */
    fun getArray(): OnDemandArray

    /** Get the root value as a string. */
    fun getString(): String

    /** Get the root value as a Long. */
    fun getLong(): Long

    /** Get the root value as a ULong. */
    fun getULong(): ULong

    /** Get the root value as a Double. */
    fun getDouble(): Double

    /** Get the root value as a Boolean. */
    fun getBoolean(): Boolean

    /** Check if the root value is null. */
    fun isNull(): Boolean

    /** Get the type of the root value. */
    fun getType(): JsonType

    override fun close()
}
