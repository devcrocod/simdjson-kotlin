package io.github.devcrocod.simdjson

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.DT_REG
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.readdir

private val testdataBase: String by lazy {
    val projectDir = getenv("PROJECT_DIR")?.toKString()
    if (projectDir != null) "$projectDir/testdata" else "testdata"
}

internal actual fun platformLoadResource(path: String): ByteArray {
    val fullPath = "$testdataBase/$path"
    val file = fopen(fullPath, "rb")
    checkNotNull(file) {
        "Test resource not found: $fullPath. " +
            "Make sure the simdjson-data submodule is initialized: git submodule update --init"
    }
    try {
        fseek(file, 0, SEEK_END)
        val rawSize = ftell(file)
        check(rawSize >= 0) { "ftell failed for $fullPath" }
        val size = rawSize.toInt()
        fseek(file, 0, SEEK_SET)
        return memScoped {
            val buffer = allocArray<ByteVar>(size)
            val read = fread(buffer, 1u.toULong(), size.toULong(), file)
            check(read.toInt() == size) { "Read $read of $size bytes from $fullPath" }
            buffer.readBytes(size)
        }
    } finally {
        fclose(file)
    }
}

internal actual fun platformListFiles(dir: String): List<String> {
    val fullPath = "$testdataBase/$dir"
    val dp = opendir(fullPath)
    checkNotNull(dp) {
        "Test resource directory not found: $fullPath. " +
            "Make sure the simdjson-data submodule is initialized: git submodule update --init"
    }
    try {
        val result = mutableListOf<String>()
        while (true) {
            val entry = readdir(dp) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name == "." || name == "..") continue
            // DT_REG is reliable on macOS and Linux (ext4/xfs/btrfs).
            // On some network filesystems d_type may return DT_UNKNOWN, silently skipping files.
            if (entry.pointed.d_type.toInt() == DT_REG) {
                result.add(name)
            }
        }
        return result
    } finally {
        closedir(dp)
    }
}
