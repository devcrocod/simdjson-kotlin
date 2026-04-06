import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.kotlin.allopen)
}

configure<AllOpenExtension> {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xadd-modules=jdk.incubator.vector"
            )
        }
    }

    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":simdjson-kotlin"))
                implementation(project(":simdjson-kotlin-serialization"))
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.jackson.databind)
                implementation(libs.jackson.module.kotlin)
            }
        }
    }
}

benchmark {
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 5
            iterationTimeUnit = "s"
            mode = "thrpt"
            outputTimeUnit = "s"
            advanced("jvmForks", 1)
        }
    }
    targets {
        register("jvm")
    }
}
