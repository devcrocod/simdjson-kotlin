import org.gradle.api.publish.internal.PublicationInternal

plugins {
    `maven-publish`
}

JNI_TARGETS.forEach { target ->
    tasks.register<Jar>("jniRuntimeJar-${target.artifactSuffix}") {
        description = "Packages JNI native library for ${target.artifactSuffix} into a JAR"
        group = "build"

        archiveBaseName.set("simdjson-kotlin-jni-runtime-${target.artifactSuffix}")
        archiveClassifier.set("")

        val nativeDir = rootProject.layout.projectDirectory.dir("simdjson-native/build-jvm-${target.os}-${target.arch}")
        from(nativeDir) {
            include(target.libFileName)
            into(target.resourceDir)
        }
    }
}

publishing {
    publications {
        JNI_TARGETS.forEach { target ->
            create<MavenPublication>("jniRuntime-${target.artifactSuffix}") {
                artifactId = "simdjson-kotlin-jni-runtime-${target.artifactSuffix}"
                artifact(tasks.named<Jar>("jniRuntimeJar-${target.artifactSuffix}"))
                // Mark as alias so Gradle's project dependency resolver ignores these
                // coordinates — mirrors what the KMP plugin does for platform publications.
                (this as PublicationInternal<*>).setAlias(true)
            }
        }
    }
}

project.gradle.projectsEvaluated {
    JNI_TARGETS.forEach { target ->
        val pubName = "jniRuntime-${target.artifactSuffix}"
        publishing.publications.named<MavenPublication>(pubName).configure {
            artifacts.removeAll { it.classifier == "javadoc" }
        }
    }
}
