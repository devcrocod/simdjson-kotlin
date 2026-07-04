# Contributing to simdjson-kotlin

Thank you for your interest in contributing to simdjson-kotlin!

simdjson-kotlin is a Kotlin Multiplatform port of [simdjson](https://github.com/simdjson/simdjson),
a high-performance JSON parser. Because it is a port, most contributions should mirror the
behavior and structure of the upstream [simdjson](https://github.com/simdjson/simdjson) (C++)
and [simdjson-java](https://github.com/simdjson/simdjson-java) implementations.

## Submitting Issues

Before creating an issue, please search [existing issues](https://github.com/devcrocod/simdjson-kotlin/issues)
to avoid duplicates. Use thumbs-up reactions on existing issues to show interest
instead of posting "+1" comments.

### Bug Reports

When reporting a bug, include:

- simdjson-kotlin version (please test against the [latest release](https://github.com/devcrocod/simdjson-kotlin/releases))
- Kotlin version and target platform (JVM, Android, macOS/Native, iOS, Linux/Native)
- On the JVM, which backend is in use (Vector API or JNI) if relevant
- Minimal reproducing code snippet, including the input JSON that triggers it
- Expected vs actual behavior

### Feature Requests

- Explain your use case — focus on the problem, not the solution.
- If the feature already exists upstream, link to the relevant simdjson (C++) or
  simdjson-java API so the port can stay faithful to it.

## Submitting Pull Requests

### Before You Start

Discuss large changes or new public API in an issue before starting work.

### Code Changes

- Target the `master` branch.
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Keep parity with the reference implementations — when porting, match the semantics of
  upstream simdjson (C++) / simdjson-java, and reference them in the PR description.
- Bug fixes must include a test that fails without the fix.
- New public API must include KDoc documentation.
- Match the surrounding code style; the codebase is largely comment-free, mirroring the
  reference implementations.
- Keep the `simdjson-kotlin` module free of external runtime dependencies.

### Native / JNI Changes

- The C/C++ glue lives in `simdjson-native/` and is built by the Gradle native tasks; the
  upstream simdjson sources are downloaded via CMake FetchContent at native-build time
  (network access required). The git submodule only provides the simdjson-data test corpus.
- Changes that touch parsing must pass on the JVM with **both** the Vector API and JNI
  backends, as well as on the native targets.

## Building & Testing

A JDK 21+ is required to run the build. Kotlin/JVM compilation uses a JDK 25 toolchain
(provisioned automatically via the Foojay resolver) and the incubating Vector API
(`jdk.incubator.vector`). Building the native and JNI artifacts also requires **CMake** and a
C++ toolchain; on Linux, the Kotlin/Native `linuxX64` target needs **g++-10** specifically.

Test data comes from a git submodule — initialize it before running tests:

```bash
git submodule update --init --recursive
```

```bash
# JVM tests — Vector API backend (default, 256-bit species)
./gradlew :simdjson-kotlin:jvmTest

# JVM tests — Vector API backend, 128-bit species
./gradlew :simdjson-kotlin:jvmTest128

# JVM tests — JNI backend (native simdjson via JNI)
./gradlew :simdjson-kotlin:jvmTestJni

# Native tests
./gradlew :simdjson-kotlin:macosArm64Test
./gradlew :simdjson-kotlin:linuxX64Test
./gradlew :simdjson-kotlin:iosSimulatorArm64Test

# Android host (unit) tests
./gradlew :simdjson-kotlin:testAndroidHostTest

# Serialization module
./gradlew :simdjson-kotlin-serialization:allTests

# Full build
./gradlew build

# Benchmarks (JVM-only)
./gradlew :benchmarks:benchmark
```

CI runs the JVM (Vector API + JNI), macOS/Linux native, iOS simulator, Android host, and
serialization test suites on every pull request to `master`.

## Code Style

- Backend selection on the JVM is controlled by the `simdjson.backend` (`vector`/`jni`) and
  `simdjson.species` (`256`/`128`) system properties — keep both backends behaving identically.
- Platform-specific code uses `expect`/`actual`; shared JVM/Android logic lives in the
  `jvmAndAndroid` source sets, native specifics behind cinterop.
- Prefer faithful ports over local rewrites; deviations from the reference algorithms should be
  justified in the PR.

## Project Structure

| Module                          | Description                                                              |
|---------------------------------|--------------------------------------------------------------------------|
| `simdjson-kotlin`               | Core KMP parser (JVM Vector API + JNI, Apple/Linux native via cinterop)  |
| `simdjson-kotlin-serialization` | kotlinx.serialization integration                                        |
| `simdjson-native`               | C/C++ glue (cinterop static lib + JNI bindings) over upstream simdjson   |
| `benchmarks`                    | JMH / kotlinx-benchmark performance benchmarks                           |
| `integration-test`             | Standalone consumer project that verifies published artifacts            |

## Contact

Use [GitHub Issues](https://github.com/devcrocod/simdjson-kotlin/issues) for questions.
