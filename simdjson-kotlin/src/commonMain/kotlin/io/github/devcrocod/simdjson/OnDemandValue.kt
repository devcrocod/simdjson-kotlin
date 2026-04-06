package io.github.devcrocod.simdjson

/**
 * A lazy JSON value that is parsed only when accessed.
 * Can only be consumed once.
 */
expect class OnDemandValue internal constructor() : AutoCloseable {

    fun getObject(): OnDemandObject
    fun getArray(): OnDemandArray
    fun getString(): String
    fun getLong(): Long
    fun getULong(): ULong
    fun getDouble(): Double
    fun getBoolean(): Boolean
    fun isNull(): Boolean
    fun getType(): JsonType

    /**
     * Materialize this value into a full DOM [JsonValue].
     * Useful when you need to store or pass around the value.
     * Consumes this On Demand value.
     */
    fun materialize(): JsonValue

    override fun close()
}
