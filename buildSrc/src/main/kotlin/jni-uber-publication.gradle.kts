import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import javax.inject.Inject

plugins {
    `maven-publish`
}

// Obtain SoftwareComponentFactory via @Inject (standard pattern for Gradle 9.x)
abstract class SoftwareComponentFactoryProvider @Inject constructor(
    val factory: SoftwareComponentFactory
)

val softwareComponentFactory = objects.newInstance<SoftwareComponentFactoryProvider>().factory
val runtimeComponent = softwareComponentFactory.adhoc("jniRuntime")

// Dependency-only configurations for each target — used solely by addVariantsFromConfiguration()
// to generate variant metadata in the .module file. Marked resolvable (not consumable) so that
// attributes {} API is available, but without conflicting with the consumable jniRuntimeElements-*
// configurations from jni-variant-attributes which share identical attribute sets.
JNI_TARGETS.forEach { target ->
    val gradleArch = when (target.arch) {
        "aarch64" -> MachineArchitecture.ARM64
        "x86_64" -> MachineArchitecture.X86_64
        else -> error("Unsupported arch: ${target.arch}")
    }

    val config = configurations.create("jniRuntimeUberVariant-${target.os}-${target.arch}") {
        isCanBeConsumed = false
        isCanBeResolved = true
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

    }

    runtimeComponent.addVariantsFromConfiguration(config) {
        mapToMavenScope("runtime")
    }
}

// Defer dependency creation: project.group and project.version are set in the build script
// body, which runs after plugin application. afterEvaluate ensures correct values.
afterEvaluate {
    JNI_TARGETS.forEach { target ->
        configurations.getByName("jniRuntimeUberVariant-${target.os}-${target.arch}").dependencies.add(
            project.dependencies.create(
                "${project.group}:simdjson-kotlin-jni-runtime-${target.artifactSuffix}:${project.version}"
            )
        )
    }
}

// Uber publication: metadata-only (.pom + .module, no JAR)
publishing {
    publications {
        create<MavenPublication>("jniRuntime") {
            from(runtimeComponent)
            artifactId = "simdjson-kotlin-jni-runtime"
            // Mark as alias so Gradle's project dependency resolver ignores these
            // coordinates — prevents "multiple publications with different coordinates" error
            // when other modules depend on this project via project(":simdjson-kotlin").
            (this as PublicationInternal<*>).setAlias(true)
        }
    }
}

project.gradle.projectsEvaluated {
    publishing.publications.named<MavenPublication>("jniRuntime").configure {
        artifacts.removeAll { it.classifier == "javadoc" }
    }
}
