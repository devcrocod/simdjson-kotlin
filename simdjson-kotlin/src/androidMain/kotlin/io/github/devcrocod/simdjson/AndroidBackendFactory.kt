package io.github.devcrocod.simdjson

internal actual fun createParserBackend(capacity: Int, maxDepth: Int): ParserBackend =
    JniBackend(capacity, maxDepth)
