import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily

JNI_TARGETS.forEach { target ->
    val gradleArch = when (target.arch) {
        "aarch64" -> MachineArchitecture.ARM64
        "x86_64" -> MachineArchitecture.X86_64
        else -> error("Unsupported arch: ${target.arch}")
    }

    configurations.create("jniRuntimeElements-${target.os}-${target.arch}") {
        isCanBeConsumed = true
        isCanBeResolved = false
        isVisible = false

        attributes {
            attribute(
                OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                objects.named(OperatingSystemFamily::class.java, target.os)
            )
            attribute(
                MachineArchitecture.ARCHITECTURE_ATTRIBUTE,
                objects.named(MachineArchitecture::class.java, gradleArch)
            )
            attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            attribute(
                Category.CATEGORY_ATTRIBUTE,
                objects.named(Category::class.java, Category.LIBRARY)
            )
            attribute(
                Bundling.BUNDLING_ATTRIBUTE,
                objects.named(Bundling::class.java, Bundling.EXTERNAL)
            )
            attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements::class.java, LibraryElements.JAR)
            )
        }

        outgoing.artifact(tasks.named("jniRuntimeJar-${target.artifactSuffix}"))
    }
}
