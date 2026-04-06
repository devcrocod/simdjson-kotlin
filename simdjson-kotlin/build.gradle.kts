import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    id("jni-runtime-publications")
    id("jni-variant-attributes")
    id("jni-uber-publication")
    id("simdjson.host-native-kmp")
}

val generateVersionProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/version")
    val projectVersion = version.toString()
    inputs.property("version", projectVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("simdjson-version.properties")
        file.parentFile.mkdirs()
        file.writeText("version=$projectVersion\n")
    }
}

kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            freeCompilerArgs.addAll("-Xadd-modules=jdk.incubator.vector", "-Xexpect-actual-classes")
        }

        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                jvmArgs("--add-modules", "jdk.incubator.vector")
                maxHeapSize = "2g"
                systemProperty("simdjson.species", "256")
            }
        }
    }

    android {
        namespace = "io.github.devcrocod.simdjson"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll("-Xexpect-actual-classes")
        }

        withHostTestBuilder {}
    }

    macosArm64 {
        compilerOptions {
            freeCompilerArgs.addAll("-Xexpect-actual-classes", "-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
        }

        compilations["main"].cinterops {
            create("simdjson") {
                defFile("src/nativeInterop/cinterop/simdjson.def")
                compilerOpts("-I${projectDir}/../simdjson-native")
                extraOpts("-libraryPath", "${projectDir}/../simdjson-native/build")
            }
        }

        binaries.all {
            linkerOpts("-lc++")
        }
    }

    iosArm64 {
        compilerOptions {
            freeCompilerArgs.addAll("-Xexpect-actual-classes", "-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
        }

        compilations["main"].cinterops {
            create("simdjson") {
                defFile = file("src/nativeInterop/cinterop/simdjson.def")
                compilerOpts("-I${projectDir}/../simdjson-native")
                extraOpts("-libraryPath", "${projectDir}/../simdjson-native/build-ios-arm64")
            }
        }

        binaries.all {
            linkerOpts("-lc++")
        }
    }

    iosSimulatorArm64 {
        compilerOptions {
            freeCompilerArgs.addAll("-Xexpect-actual-classes", "-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
        }

        compilations["main"].cinterops {
            create("simdjson") {
                defFile = file("src/nativeInterop/cinterop/simdjson.def")
                compilerOpts("-I${projectDir}/../simdjson-native")
                extraOpts("-libraryPath", "${projectDir}/../simdjson-native/build-ios-simulator-arm64")
            }
        }

        binaries.all {
            linkerOpts("-lc++")
        }
    }

    linuxX64 {
        compilerOptions {
            freeCompilerArgs.addAll("-Xexpect-actual-classes", "-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
        }

        compilations["main"].cinterops {
            create("simdjson") {
                defFile = file("src/nativeInterop/cinterop/simdjson.def")
                compilerOpts("-I${projectDir}/../simdjson-native")
                extraOpts("-libraryPath", "${projectDir}/../simdjson-native/build-linux-x64")
            }
        }

        binaries.all {
            linkerOpts("-lstdc++")
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain { }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.assertions.core)
            }
        }
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        val jvmAndAndroidTest by creating {
            dependsOn(commonTest.get())
        }
        jvmMain {
            dependsOn(jvmAndAndroidMain)
            resources.srcDir(generateVersionProperties.map { it.outputs.files.singleFile })
            dependencies {
                implementation(kotlin("reflect"))
                runtimeOnly("${project.group}:simdjson-kotlin-jni-runtime:${project.version}")
            }
        }
        jvmTest {
            dependsOn(jvmAndAndroidTest)
        }
        androidMain {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                implementation(kotlin("reflect"))
            }
        }
    }
}

// --- Android NDK build task for simdjson JNI ---

// Android JNI native library output — placed in src/androidMain/jniLibs/ (AGP convention).
// These are build artifacts, not source — excluded from git via .gitignore.
val jniLibsDir = layout.projectDirectory.dir("src/androidMain/jniLibs")
val nativeDir = layout.projectDirectory.dir("../simdjson-native")

val buildSimdjsonJni by tasks.registering(Exec::class) {
    description = "Build simdjson JNI shared libraries for Android via build-android.sh"
    group = "build"

    inputs.files(
        nativeDir.file("simdjson_c.h"),
        nativeDir.file("simdjson_c.cpp"),
        nativeDir.file("simdjson_jni.cpp"),
        nativeDir.file("CMakeLists-android.txt"),
        nativeDir.file("build-android.sh")
    )
    outputs.dir(jniLibsDir)

    commandLine("bash", "${nativeDir.asFile.absolutePath}/build-android.sh")

    doLast {
        val abis = listOf("arm64-v8a", "x86_64")
        for (abi in abis) {
            val src = file("${nativeDir.asFile.absolutePath}/build-android-$abi/libsimdjson_jni.so")
            val destDir = jniLibsDir.dir(abi).asFile
            destDir.mkdirs()
            src.copyTo(File(destDir, "libsimdjson_jni.so"), overwrite = true)
        }
    }
}

// --- Desktop JVM JNI build task ---

// JNI native library output — placed under build/ (not src/) so it never leaks into
// the published JVM JAR or into git. Added to the test classpath explicitly.
val jniNativesDir = layout.buildDirectory.dir("jniNatives")

val buildSimdjsonJniDesktop by tasks.registering(Exec::class) {
    description = "Build simdjson JNI shared library for the current desktop platform"

    inputs.files(
        nativeDir.file("simdjson_c.h"),
        nativeDir.file("simdjson_c.cpp"),
        nativeDir.file("simdjson_jni.cpp"),
        nativeDir.file("CMakeLists-jvm.txt"),
        nativeDir.file("build-jvm.sh")
    )
    outputs.dir(jniNativesDir)

    commandLine("bash", "${nativeDir.asFile.absolutePath}/build-jvm.sh")

    doLast {
        val os = when {
            org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "macos"
            org.gradle.internal.os.OperatingSystem.current().isLinux -> "linux"
            org.gradle.internal.os.OperatingSystem.current().isWindows -> "windows"
            else -> error("Unsupported OS")
        }
        val arch = when (System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "aarch64"
            "amd64", "x86_64" -> "x86_64"
            else -> error("Unsupported arch: ${System.getProperty("os.arch")}")
        }
        val ext = when (os) {
            "macos" -> "dylib"
            "linux" -> "so"
            "windows" -> "dll"
            else -> error("Unsupported OS")
        }
        val libName = if (os == "windows") "simdjson_jni.$ext" else "libsimdjson_jni.$ext"
        val src = file("${nativeDir.asFile.absolutePath}/build-jvm-${os}-${arch}/$libName")
        val destDir = jniNativesDir.get().dir("natives/${os}-${arch}").asFile
        destDir.mkdirs()
        src.copyTo(File(destDir, libName), overwrite = true)
    }
}

// --- JNI backend test task ---

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

// --- Kotlin/Native static library build tasks for cinterop ---

val nativeStaticInputs = listOf(
    nativeDir.file("simdjson_c.h"),
    nativeDir.file("simdjson_c.cpp"),
    nativeDir.file("CMakeLists.txt")
)

val buildSimdjsonNativeMacosArm64 by tasks.registering(Exec::class) {
    description = "Build simdjson_c static library for macOS ARM64"
    group = "build"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    inputs.files(nativeStaticInputs)
    outputs.file(nativeDir.file("build/libsimdjson_c.a"))

    workingDir = nativeDir.asFile
    commandLine(
        "bash", "-c",
        "cmake -S . -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build --parallel"
    )
}

val buildSimdjsonNativeIos by tasks.registering(Exec::class) {
    description = "Build simdjson_c static libraries for iOS (device + simulator)"
    group = "build"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    inputs.files(nativeStaticInputs + nativeDir.file("build-ios.sh"))
    outputs.files(
        nativeDir.file("build-ios-arm64/libsimdjson_c.a"),
        nativeDir.file("build-ios-simulator-arm64/libsimdjson_c.a")
    )

    workingDir = nativeDir.asFile
    commandLine("bash", "${nativeDir.asFile.absolutePath}/build-ios.sh")
}

val buildSimdjsonNativeLinuxX64 by tasks.registering(Exec::class) {
    description = "Build simdjson_c static library for Linux x64"
    group = "build"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }

    inputs.files(nativeStaticInputs)
    outputs.file(nativeDir.file("build-linux-x64/libsimdjson_c.a"))

    workingDir = nativeDir.asFile
    commandLine(
        "bash", "-c",
        "cmake -S . -B build-linux-x64 -DCMAKE_BUILD_TYPE=Release && cmake --build build-linux-x64 --parallel"
    )
}

val buildSimdjsonNative by tasks.registering {
    description = "Build all simdjson_c static libraries for Kotlin/Native cinterop targets"
    group = "build"

    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        dependsOn(buildSimdjsonNativeMacosArm64, buildSimdjsonNativeIos)
    }
    if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
        dependsOn(buildSimdjsonNativeLinuxX64)
    }
}

// Wire cinterop tasks to depend on native builds
tasks.named("cinteropSimdjsonMacosArm64") { dependsOn(buildSimdjsonNativeMacosArm64) }
tasks.named("cinteropSimdjsonIosArm64") { dependsOn(buildSimdjsonNativeIos) }
tasks.named("cinteropSimdjsonIosSimulatorArm64") { dependsOn(buildSimdjsonNativeIos) }
tasks.named("cinteropSimdjsonLinuxX64") { dependsOn(buildSimdjsonNativeLinuxX64) }

// Exclude JNI runtime Maven artifact from test configurations — the native library
// is provided via local build resources on the classpath instead.
configurations.matching { it.name.contains("jvmTestRuntime") }.configureEach {
    exclude(group = project.group.toString(), module = "simdjson-kotlin-jni-runtime")
}

// Wire desktop JNI build so tests can load the native library from the classpath.
// The native lib lives under build/jniNatives/natives/{os}-{arch}/ — NOT in src/jvmMain/resources/,
// so it never leaks into the published JVM JAR.
tasks.named<Test>("jvmTest") {
    dependsOn(buildSimdjsonJniDesktop)
    classpath += files(jniNativesDir)
}
tasks.named<Test>("jvmTestJni") {
    dependsOn(buildSimdjsonJniDesktop)
    classpath += files(jniNativesDir)
}

// Wire desktop JNI build so the host-platform runtime JAR includes the native library.
// Only the current host's JAR task needs this — cross-platform JARs are filled by CI.
val hostJniTarget = JNI_TARGETS.find { t ->
    val hostOs = when {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "macos"
        org.gradle.internal.os.OperatingSystem.current().isLinux -> "linux"
        org.gradle.internal.os.OperatingSystem.current().isWindows -> "windows"
        else -> ""
    }
    val hostArch = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x86_64"
        else -> ""
    }
    t.os == hostOs && t.arch == hostArch
}
if (hostJniTarget != null) {
    tasks.named("jniRuntimeJar-${hostJniTarget.artifactSuffix}") { dependsOn(buildSimdjsonJniDesktop) }
}

// Android host tests run on the host JVM, not on a device — they need a host-native
// library, not the NDK-built Android .so.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    val currentOs = org.gradle.internal.os.OperatingSystem.current()
    onlyIf { currentOs.isLinux || currentOs.isMacOsX }

    val os = when {
        currentOs.isMacOsX -> "macos"
        currentOs.isLinux -> "linux"
        else -> "unsupported"
    }
    val arch = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x86_64"
        else -> "unsupported"
    }

    // On Linux CI the desktop .so must be built first; on macOS it is pre-built.
    if (currentOs.isLinux) {
        dependsOn(buildSimdjsonJniDesktop)
    }

    (this as Test).jvmArgs(
        "-Djava.library.path=${nativeDir.dir("build-jvm-$os-$arch").asFile.absolutePath}"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
}

// Ensure native test working directory is module root (where testdata/ lives).
// simctl spawn ignores CWD, so also pass it via env var (the Gradle plugin
// adds the SIMCTL_CHILD_ prefix automatically for simulator tests).
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    workingDir = projectDir.absolutePath
    // simctl spawn only passes SIMCTL_CHILD_-prefixed vars to the spawned process
    // (it strips the prefix, so the binary sees PROJECT_DIR).
    environment("SIMCTL_CHILD_PROJECT_DIR", projectDir.absolutePath)
}
