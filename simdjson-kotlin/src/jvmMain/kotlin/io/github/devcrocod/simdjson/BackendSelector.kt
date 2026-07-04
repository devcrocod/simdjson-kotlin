package io.github.devcrocod.simdjson

internal object BackendSelector {
    val useJni: Boolean = run {
        val decision = decide(
            forced = System.getProperty("simdjson.backend"),
            jdkFeature = Runtime.version().feature(),
            vectorModulePresent = ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent,
            vectorUsable = ::vectorUsable,
        )
        decision.hint?.let {
            System.getLogger(BackendSelector::class.java.name).log(System.Logger.Level.INFO, it)
        }
        decision.useJni
    }

    internal data class Decision(val useJni: Boolean, val hint: String?)

    internal fun decide(
        forced: String?,
        jdkFeature: Int,
        vectorModulePresent: Boolean,
        vectorUsable: () -> Boolean,
    ): Decision = when {
        forced == "jni" -> Decision(useJni = true, hint = null)
        forced == "vector" -> Decision(useJni = false, hint = null)
        jdkFeature < 24 -> Decision(useJni = true, hint = null)
        !vectorModulePresent -> Decision(
            useJni = true,
            hint = "The jdk.incubator.vector module is not available; falling back to the JNI backend. " +
                "Add '--add-modules jdk.incubator.vector' to the JVM options to enable the Vector backend.",
        )
        !vectorUsable() -> Decision(
            useJni = true,
            hint = "The Vector API is available but not usable on this JVM; falling back to the JNI backend.",
        )
        else -> Decision(useJni = false, hint = null)
    }

    private fun vectorUsable(): Boolean =
        try {
            VectorUtils.preferredBitSize() >= 128
        } catch (t: Throwable) {
            false
        }
}
