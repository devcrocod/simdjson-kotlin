import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(25)

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                jvmArgs("--add-modules", "jdk.incubator.vector")
                maxHeapSize = "2g"
                systemProperty("simdjson.species", "256")
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotlin-test").get())
            implementation(libs.findLibrary("kotest-assertions-core").get())
        }
    }
}

tasks.register<Test>("jvmTestJni") {
    description = "Run JVM tests with JNI backend"
    group = "verification"

    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath

    useJUnitPlatform()
    jvmArgs("--add-modules", "jdk.incubator.vector")
    maxHeapSize = "2g"
    systemProperty("simdjson.backend", "jni")
    systemProperty("simdjson.species", "256")
}

// Exclude JNI runtime Maven artifact from test configurations — the native library
// is provided via local build resources on the classpath instead.
configurations.matching { it.name.contains("jvmTestRuntime") }.configureEach {
    exclude(group = project.group.toString(), module = "simdjson-kotlin-jni-runtime")
}
