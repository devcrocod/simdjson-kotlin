import org.gradle.api.GradleException

enum class SimdjsonNativeTarget(val targetName: String) {
    MACOS_ARM64("macosArm64"),
    IOS_ARM64("iosArm64"),
    IOS_SIMULATOR_ARM64("iosSimulatorArm64"),
    LINUX_X64("linuxX64"),
}

object HostDetection {
    val hostOs: String = System.getProperty("os.name")
    val hostArch: String = System.getProperty("os.arch")

    val isMacOs: Boolean get() = hostOs == "Mac OS X"
    val isLinux: Boolean get() = hostOs == "Linux"

    val hostTargets: List<SimdjsonNativeTarget>
        get() = when {
            isMacOs -> listOf(
                SimdjsonNativeTarget.MACOS_ARM64,
                SimdjsonNativeTarget.IOS_ARM64,
                SimdjsonNativeTarget.IOS_SIMULATOR_ARM64,
            )
            isLinux -> listOf(
                SimdjsonNativeTarget.LINUX_X64,
            )
            else -> throw GradleException(
                "Unsupported host OS: $hostOs ($hostArch). " +
                    "Supported: macOS (builds macosArm64, iosArm64, iosSimulatorArm64), " +
                    "Linux (builds linuxX64)."
            )
        }
}
