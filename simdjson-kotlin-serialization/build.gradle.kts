plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(25)

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                jvmArgs("--add-modules", "jdk.incubator.vector")
                systemProperty("simdjson.species", "256")
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":simdjson-kotlin"))
                implementation(libs.kotlinx.serialization.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}

// Exclude JNI runtime Maven artifact from test configurations — the native library
// is provided via local build resources on the classpath instead.
configurations.matching { it.name.contains("jvmTestRuntime") }.configureEach {
    exclude(group = project.group.toString(), module = "simdjson-kotlin-jni-runtime")
}

val jvmTestJni by tasks.registering(Test::class) {
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
