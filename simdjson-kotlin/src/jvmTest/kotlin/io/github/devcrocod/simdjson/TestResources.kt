package io.github.devcrocod.simdjson

import java.io.File

internal actual fun platformLoadResource(path: String): ByteArray {
    val file = File("testdata/$path")
    check(file.isFile) {
        "Test resource not found: ${file.absolutePath}. " +
            "Make sure the simdjson-data submodule is initialized: git submodule update --init"
    }
    return file.readBytes()
}

internal actual fun platformListFiles(dir: String): List<String> {
    val directory = File("testdata/$dir")
    check(directory.isDirectory) {
        "Test resource directory not found: ${directory.absolutePath}"
    }
    return directory.listFiles()
        ?.filter { it.isFile }
        ?.map { it.name }
        ?: emptyList()
}

