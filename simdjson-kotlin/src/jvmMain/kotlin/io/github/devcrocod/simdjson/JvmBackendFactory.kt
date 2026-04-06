package io.github.devcrocod.simdjson

internal actual fun createParserBackend(capacity: Int, maxDepth: Int): ParserBackend =
    if (BackendSelector.useJni) JniBackend(capacity, maxDepth)
    else VectorBackend(capacity, maxDepth)
