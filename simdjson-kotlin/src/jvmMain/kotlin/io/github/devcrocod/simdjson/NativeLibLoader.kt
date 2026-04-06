package io.github.devcrocod.simdjson

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties

internal object NativeLibLoader {

    private const val LIB_BASE_NAME = "simdjson_jni"

    private val SUPPORTED_PLATFORMS = listOf(
        "macos-aarch64", "macos-x86_64",
        "linux-x86_64", "linux-aarch64",
        "windows-x86_64"
    )

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            load()
            loaded = true
        }
    }

    private fun load() {
        // 1. Try system library path first (allows user override via -Djava.library.path)
        try {
            System.loadLibrary(LIB_BASE_NAME)
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to resource extraction
        }

        val os = detectOs()
        val arch = detectArch()
        val libName = libraryName(os)

        // 2. Check -Dsimdjson.native.dir escape hatch
        val nativeDir = System.getProperty("simdjson.native.dir")
        if (nativeDir != null) {
            loadFromDir(Paths.get(nativeDir), libName)
            return
        }

        // 3. Resolve versioned cache directory
        val version = loadVersion()
        val cacheDir = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "simdjson-kotlin-$version",
            "$os-$arch"
        )
        Files.createDirectories(cacheDir)
        val libPath = cacheDir.resolve(libName)

        // 4. Check if already cached
        if (Files.exists(libPath)) {
            loadLib(libPath, libName)
            return
        }

        // 5. Acquire file lock and extract
        val lockPath = cacheDir.resolve("$libName.lock")
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                // Re-check after acquiring lock (another process may have extracted)
                if (!Files.exists(libPath)) {
                    extractFromClasspath(os, arch, libName, libPath)
                }
            }
        }

        // 6. Load the cached library
        loadLib(libPath, libName)
    }

    private fun extractFromClasspath(os: String, arch: String, libName: String, target: Path) {
        val resourcePath = "natives/$os-$arch/$libName"
        val stream = NativeLibLoader::class.java.classLoader?.getResourceAsStream(resourcePath)
            ?: throw NativeLibraryException(
                "Native library not found in classpath: $resourcePath\n" +
                        "Ensure the correct simdjson-kotlin runtime artifact is on the classpath for your platform.\n" +
                        "Supported platforms: ${SUPPORTED_PLATFORMS.joinToString(", ")}.\n" +
                        "Alternatively, set -Dsimdjson.native.dir=<path> to provide the library manually."
            )

        val tmpFile = target.resolveSibling("$libName.tmp")
        tmpFile.toFile().deleteOnExit()
        stream.use { Files.copy(it, tmpFile, StandardCopyOption.REPLACE_EXISTING) }
        Files.move(tmpFile, target, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun loadFromDir(dir: Path, libName: String) {
        val libPath = dir.resolve(libName)
        if (!Files.exists(libPath)) {
            throw NativeLibraryException(
                "Native library not found at ${libPath.toAbsolutePath()} " +
                        "(specified via -Dsimdjson.native.dir).\n" +
                        "Ensure the library file exists at that location."
            )
        }
        loadLib(libPath, libName)
    }

    private fun loadLib(path: Path, libName: String) {
        try {
            System.load(path.toAbsolutePath().toString())
        } catch (e: UnsatisfiedLinkError) {
            throw NativeLibraryException(
                "Failed to load native library from ${path.toAbsolutePath()}: ${e.message}\n" +
                        "If this is a permissions issue, try setting -Dsimdjson.native.dir " +
                        "to a directory with exec permissions.",
                e
            )
        }
    }

    private fun loadVersion(): String {
        val props = Properties()
        val stream = NativeLibLoader::class.java.classLoader
            ?.getResourceAsStream("simdjson-version.properties")
        if (stream != null) {
            stream.use { props.load(it) }
        }
        return props.getProperty("version", "unknown")
    }

    private fun detectOs(): String {
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> "macos"
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> throw NativeLibraryException(
                "Unsupported platform: $os/${System.getProperty("os.arch", "unknown")}\n" +
                        "Supported platforms: ${SUPPORTED_PLATFORMS.joinToString(", ")}."
            )
        }
    }

    private fun detectArch(): String {
        val arch = System.getProperty("os.arch", "")
        return when (arch) {
            "aarch64", "arm64" -> "aarch64"
            "amd64", "x86_64" -> "x86_64"
            else -> throw NativeLibraryException(
                "Unsupported platform: ${System.getProperty("os.name", "unknown")}/$arch\n" +
                        "Supported platforms: ${SUPPORTED_PLATFORMS.joinToString(", ")}."
            )
        }
    }

    private fun libraryName(os: String): String = when (os) {
        "macos" -> "libsimdjson_jni.dylib"
        "linux" -> "libsimdjson_jni.so"
        "windows" -> "simdjson_jni.dll"
        else -> throw NativeLibraryException(
            "Unsupported platform: $os/${System.getProperty("os.arch", "unknown")}\n" +
                    "Supported platforms: ${SUPPORTED_PLATFORMS.joinToString(", ")}."
        )
    }
}
