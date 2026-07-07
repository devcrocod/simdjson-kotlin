---
title: Installation
description: Gradle coordinates, platform requirements, and the supported-platform matrix.
weight: 10
---

## Add the dependency

simdjson-kotlin is published to Maven Central. Add the core module to your
`commonMain` source set:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.devcrocod:simdjson-kotlin:0.1.0")
        }
        // optional: kotlinx.serialization support (currently JVM-only)
        jvmMain.dependencies {
            implementation("io.github.devcrocod:simdjson-kotlin-serialization:0.1.0")
        }
    }
}
```

The core module gives you the [DOM](../../guides/dom/) and
[On-Demand](../../guides/on-demand/) APIs on every supported target. The
optional `simdjson-kotlin-serialization` module adds
[kotlinx.serialization](../../guides/serialization/) decoding on the JVM.

## Requirements

{{< params >}}
JVM|JDK 11+|On JDK 24+ the parser uses the pure-Kotlin Vector API backend — add `--add-modules jdk.incubator.vector` to enable it. On older JDKs, or when the Vector API is unavailable, it transparently falls back to a JNI backend with bundled native simdjson libraries (pulled in transitively via `simdjson-kotlin-jni-runtime`).
Android|minSdk 26|Uses the JNI backend.
Kotlin|2.4|The library is built with Kotlin 2.4.
{{< /params >}}

{{< callout type="tip" title="Forcing a backend" >}}
On the JVM you can force the parser backend with the system property
`-Dsimdjson.backend=vector` or `-Dsimdjson.backend=jni`.
{{< /callout >}}

## Supported platforms

| Target                                      | Implementation                |
|---------------------------------------------|-------------------------------|
| JVM (JDK 24+)                               | Vector API (pure Kotlin SIMD) |
| JVM (JDK 11–23)                             | JNI → native simdjson         |
| Android                                     | JNI → native simdjson         |
| macosArm64 / iosArm64 / iosSimulatorArm64   | cinterop → simdjson C++       |
| linuxX64                                    | cinterop → simdjson C++       |
