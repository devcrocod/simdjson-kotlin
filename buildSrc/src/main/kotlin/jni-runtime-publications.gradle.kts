import org.gradle.api.publish.internal.PublicationInternal

plugins {
    `maven-publish`
}

val requireAllNatives = (findProperty("simdjson.allTargets") as? String)?.toBoolean() ?: false

fun jniNativeDir(target: JniTarget) =
    rootProject.layout.projectDirectory.dir("simdjson-native/build-jvm-${target.os}-${target.arch}")

val perPlatformJars = JNI_TARGETS.associateWith { target ->
    tasks.register<Jar>("jniRuntimeJar-${target.artifactSuffix}") {
        description = "Packages the JNI native library for ${target.artifactSuffix} into a classifier JAR"
        group = "build"

        archiveBaseName.set("simdjson-kotlin-jni-runtime")
        archiveClassifier.set(target.artifactSuffix)

        from(jniNativeDir(target)) {
            include(target.libFileName)
            into(target.resourceDir)
        }
    }
}

val jniRuntimeJar = tasks.register<Jar>("jniRuntimeJar") {
    description = "Packages all JNI native libraries into a single runtime JAR"
    group = "build"

    archiveBaseName.set("simdjson-kotlin-jni-runtime")
    archiveClassifier.set("")

    JNI_TARGETS.forEach { target ->
        from(jniNativeDir(target)) {
            include(target.libFileName)
            into(target.resourceDir)
        }
    }

    doFirst {
        if (requireAllNatives) {
            val missing = JNI_TARGETS.filterNot { jniNativeDir(it).file(it.libFileName).asFile.exists() }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Missing JNI native libraries for: ${missing.joinToString { it.artifactSuffix }}. " +
                        "Refusing to publish an incomplete simdjson-kotlin-jni-runtime JAR — " +
                        "ensure build-natives staged every platform's library."
                )
            }
        }
    }
}

val jniRuntimeSourcesJar = tasks.register<Jar>("jniRuntimeSourcesJar") {
    description = "Empty sources JAR for the JNI runtime module (Maven Central requirement)"
    group = "build"
    archiveBaseName.set("simdjson-kotlin-jni-runtime")
    archiveClassifier.set("sources")
}

publishing {
    publications {
        create<MavenPublication>("jniRuntime") {
            artifactId = "simdjson-kotlin-jni-runtime"
            artifact(jniRuntimeJar)
            artifact(jniRuntimeSourcesJar)
            JNI_TARGETS.forEach { target ->
                artifact(perPlatformJars.getValue(target))
            }
            // Alias so project(":simdjson-kotlin") dependencies don't clash on coordinates.
            (this as PublicationInternal<*>).setAlias(true)
        }
    }
}
