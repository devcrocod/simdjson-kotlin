plugins {
    id("simdjson.kmp-base")
    alias(libs.plugins.kotlinx.serialization)
    id("simdjson.maven-publish")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":simdjson-kotlin"))
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }
}
