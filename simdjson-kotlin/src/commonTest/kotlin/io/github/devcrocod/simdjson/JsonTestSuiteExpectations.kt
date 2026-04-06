package io.github.devcrocod.simdjson

/** Returns the set of i_ minefield files expected to parse successfully on this platform. */
internal expect fun platformIFilesExpectedToPass(): Set<String>
