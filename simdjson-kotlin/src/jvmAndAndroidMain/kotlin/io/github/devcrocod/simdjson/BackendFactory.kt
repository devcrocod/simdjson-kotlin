package io.github.devcrocod.simdjson

internal expect fun createParserBackend(capacity: Int, maxDepth: Int): ParserBackend
