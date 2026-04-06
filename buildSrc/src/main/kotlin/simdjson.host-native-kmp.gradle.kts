// simdjson.allTargets=true bypasses filtering (for CI publishing with pre-built binaries)
val allTargets = (findProperty("simdjson.allTargets") as? String)?.toBoolean() ?: false

if (!allTargets) {
    val hostTargetNames = HostDetection.hostTargets.map { it.targetName }.toSet()
    val nonHostTargetNames = SimdjsonNativeTarget.entries.map { it.targetName }.toSet() - hostTargetNames

    // Task types from the Kotlin Gradle plugin that should be disabled for non-host targets.
    // Matched by superclass name because Gradle decorates task classes with _Decorated suffix.
    val nativeTaskTypes = setOf("KotlinNativeCompile", "KotlinNativeLink", "CInteropProcess")

    tasks.configureEach {
        val className = this::class.java.superclass?.simpleName ?: this::class.simpleName
        if (className in nativeTaskTypes) {
            val isNonHost = nonHostTargetNames.any { name.contains(it, ignoreCase = true) }
            if (isNonHost) {
                enabled = false
                onlyIf { false }
            }
        }
    }
}
