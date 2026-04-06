package io.github.devcrocod.simdjson.annotations

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonFieldName(val value: String)
