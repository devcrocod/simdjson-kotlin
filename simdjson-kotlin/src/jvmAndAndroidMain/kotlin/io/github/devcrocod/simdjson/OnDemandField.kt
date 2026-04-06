package io.github.devcrocod.simdjson

actual class OnDemandField internal actual constructor() {

    private lateinit var _name: String
    private lateinit var _value: OnDemandValue

    internal fun init(name: String, value: OnDemandValue) {
        _name = name
        _value = value
    }

    actual val name: String get() = _name
    actual val value: OnDemandValue get() = _value
}
