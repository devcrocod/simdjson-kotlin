plugins {
    kotlin("jvm") version "2.4.0"
    application
}

val simdjsonVersion = providers.gradleProperty("simdjsonVersion").getOrElse("0.1.0")
val pinClassifier = providers.gradleProperty("pinClassifier").orNull

dependencies {
    if (pinClassifier == null) {
        implementation("io.github.devcrocod:simdjson-kotlin-jvm:$simdjsonVersion")
    } else {
        implementation("io.github.devcrocod:simdjson-kotlin-jvm:$simdjsonVersion") {
            exclude(group = "io.github.devcrocod", module = "simdjson-kotlin-jni-runtime")
        }
        runtimeOnly("io.github.devcrocod:simdjson-kotlin-jni-runtime:$simdjsonVersion:$pinClassifier")
    }
}

application {
    mainClass.set("MainKt")
}

// Force the JNI backend (so the run actually exercises native loading regardless of JDK), and
// isolate NativeLibLoader's extraction cache ($java.io.tmpdir/simdjson-kotlin-*) per invocation.
// Without a fresh cache, a native extracted by an earlier run would be reused and would mask a
// missing/broken classifier dependency — the manual-override run must re-extract from its own
// resolved classpath to be a meaningful test.
tasks.named<JavaExec>("run") {
    systemProperty("simdjson.backend", "jni")
    val cacheDir = layout.buildDirectory.dir("native-cache-${pinClassifier ?: "auto"}")
    systemProperty("java.io.tmpdir", cacheDir.get().asFile.absolutePath)
    doFirst {
        cacheDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
