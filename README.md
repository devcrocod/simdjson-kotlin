simdjson-kotlin
===============
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devcrocod/simdjson-kotlin)](https://central.sonatype.com/search?namespace=io.github.devcrocod)
[![License](https://img.shields.io/badge/License-Apache%202-blue.svg)](LICENSE)

A Kotlin Multiplatform version of [simdjson](https://github.com/simdjson/simdjson) - a JSON parser using SIMD instructions,
based on the paper [Parsing Gigabytes of JSON per Second](https://arxiv.org/abs/1902.08318)
by Geoff Langdale and Daniel Lemire. The JVM implementation is based on
[simdjson-java](https://github.com/simdjson/simdjson-java).

> [!WARNING]
> simdjson-kotlin is in early development. Following the [SemVer specification](https://semver.org/),
> a major version of zero means initial development - the API should not be considered stable.

## Code Samples

### DOM API

Parses the whole document into an immutable tree:

```kotlin
val json: ByteArray = loadTwitterJson()

SimdJsonParser().use { parser ->
    val root = parser.parse(json) as JsonObject
    val statuses = root["statuses"] as JsonArray
    for (tweet in statuses) {
        val user = (tweet as JsonObject)["user"] as JsonObject
        if ((user["default_profile"] as JsonBoolean).value) {
            println((user["screen_name"] as JsonString).value)
        }
    }
}
```

### On-Demand API

Lazy, forward-only iteration - values are decoded only when accessed, without building a tree:

```kotlin
val json: ByteArray = loadTwitterJson()

SimdJsonParser().use { parser ->
    parser.iterate(json).use { doc ->
        for (tweet in doc.getObject()["statuses"].getArray()) {
            val user = tweet.getObject()["user"].getObject()
            if (user["default_profile"].getBoolean()) {
                println(user["screen_name"].getString())
            }
        }
    }
}
```

### kotlinx.serialization

The `simdjson-kotlin-serialization` module (currently JVM-only) decodes `@Serializable` classes directly.
Decoding only - simdjson-kotlin is a parser, use `kotlinx.serialization.json` for encoding:

```kotlin
@Serializable
data class Twitter(val statuses: List<Status>)

@Serializable
data class Status(val user: User)

@Serializable
data class User(val default_profile: Boolean, val screen_name: String)

val json: String = loadTwitterJson()

val simdJson = SimdJson { ignoreUnknownKeys = true }
for (status in simdJson.decodeFromString<Twitter>(json).statuses) {
    if (status.user.default_profile) {
        println(status.user.screen_name)
    }
}
```

## Installation

To include the library in your project, add the following to the `build.gradle.kts` file:

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

## Requirements

- **JVM**: JDK 11 or later. On JDK 24+ the parser uses the pure-Kotlin [Vector API](https://openjdk.org/jeps/489)
  backend - add `--add-modules jdk.incubator.vector` to the JVM options to enable it. On older JDKs, or when
  the Vector API is unavailable, it transparently falls back to a JNI backend with bundled native simdjson
  libraries (pulled in transitively via `simdjson-kotlin-jni-runtime`). The backend can be forced with
  `-Dsimdjson.backend=vector|jni`.
- **Android**: minSdk 26, JNI backend.
- The library is built with Kotlin 2.4.

## Supported Platforms

| Target                                    | Implementation                |
|-------------------------------------------|-------------------------------|
| JVM (JDK 24+)                             | Vector API (pure Kotlin SIMD) |
| JVM (JDK 11-23)                           | JNI → native simdjson         |
| Android                                   | JNI → native simdjson         |
| macosArm64 / iosArm64 / iosSimulatorArm64 | cinterop → simdjson C++       |
| linuxX64                                  | cinterop → simdjson C++       |

## Building & Testing

Test data comes from a git submodule, so clone with `--recursive`
(or run `git submodule update --init --recursive` in an existing checkout):

```bash
git clone --recursive https://github.com/devcrocod/simdjson-kotlin.git
```

To run the tests, execute the following commands:

```bash
./gradlew :simdjson-kotlin:jvmTest   # JVM tests
./gradlew build                      # full build for the current host
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full test matrix and native toolchain requirements.

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting
issues or pull requests. This project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Licensed under the [Apache License 2.0](LICENSE), the same license as the original
[simdjson](https://github.com/simdjson/simdjson).
