package io.github.devcrocod.simdjson

internal object BackendSelector {
    val useJni: Boolean = run {
        val prop = System.getProperty("simdjson.backend")
        when (prop) {
            "jni" -> true
            "vector" -> false
            else -> Runtime.version().feature() < 24
        }
    }
}
