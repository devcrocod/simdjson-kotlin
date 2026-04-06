package io.github.devcrocod.simdjson

/**
 * A single field in an On Demand object.
 */
expect class OnDemandField internal constructor() {

    /** Field name (always copied to String). */
    val name: String

    /** Field value. */
    val value: OnDemandValue
}
