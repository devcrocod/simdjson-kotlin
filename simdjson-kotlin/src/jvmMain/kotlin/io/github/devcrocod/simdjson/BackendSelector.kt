package io.github.devcrocod.simdjson

internal object BackendSelector {
    val useJni: Boolean = run {
        when (System.getProperty("simdjson.backend")) {
            "jni" -> true
            "vector" -> false
            else -> Runtime.version().feature() < 24 || !vectorUsable()
        }
    }

    private fun vectorUsable(): Boolean =
        try {
            VectorUtils.preferredBitSize() >= 128
        } catch (t: Throwable) {
            false
        }
}
