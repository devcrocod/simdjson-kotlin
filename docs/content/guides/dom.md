---
title: DOM API
description: Parse a whole document into an immutable, random-access tree of JsonValues.
weight: 10
---

The DOM API parses an entire document into an immutable in-memory tree. Every
node is a [`JsonValue`](#the-jsonvalue-tree) you can navigate and re-read
freely — random access, multiple passes, no ordering constraints.

## Parsing

`SimdJsonParser.parse` accepts either a `String` or a UTF-8 `ByteArray` and
returns the root `JsonValue`:

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

{{< callout type="tip" title="Bytes beat strings" >}}
Parsing a UTF-8 `ByteArray` directly skips a `String` decode step. `parse` takes
an optional `length`, so you can reuse an oversized buffer:
`parser.parse(buffer, length = bytesRead)`.
{{< /callout >}}

## The JsonValue tree

`JsonValue` is a `sealed` type with one subtype per JSON kind:

{{< params >}}
JsonObject|Iterable<Pair<String, JsonValue>>|Keyed map: `get(key): JsonValue?`, `size`, `keys(): Set<String>`, `key in obj`, and iteration over `(key, value)` pairs.
JsonArray|Iterable<JsonValue>|Ordered list: `get(index): JsonValue`, `size`, and iteration over elements.
JsonString|value class|Wraps `value: String`.
JsonNumber|—|A number that remembers its representation — see [Numbers](#numbers).
JsonBoolean|value class|Wraps `value: Boolean`.
JsonNull|object|The `null` literal (a singleton).
{{< /params >}}

Because the hierarchy is sealed, a `when` over it is exhaustive:

```kotlin
val text: String? = when (val v = parser.parse(json)) {
    is JsonObject -> "object with ${v.size} entries"
    is JsonArray -> "array of ${v.size}"
    is JsonString -> v.value
    is JsonNumber -> v.toDouble().toString()
    is JsonBoolean -> v.value.toString()
    JsonNull -> null
}
```

`get` on a `JsonObject` returns `JsonValue?` (`null` when the key is absent).
Indexing a `JsonArray` returns a non-null `JsonValue`. Casting to the wrong
subtype throws a `ClassCastException`, so guard with `as?` or an `is` check when
the shape is uncertain.

## Numbers

`JsonNumber` preserves the parsed number's representation so you can read it
back without loss:

{{< params >}}
isInteger|Boolean|`true` for integers, signed or unsigned.
isLong|Boolean|`true` for signed 64-bit integers.
isUnsigned|Boolean|`true` for values that only fit in an unsigned 64-bit integer.
toLong() · toULong() · toInt()|—|Integer views of the value.
toDouble()|—|Floating-point view of the value.
{{< /params >}}

## Lifetime

The DOM tree is fully materialized and **independent of the parser** — it stays
valid after later `parse`/`iterate` calls and after the parser is closed. You
still close the *parser* itself — it owns reusable buffers and, on Native,
native memory — and `use { }` does that for you.

{{< callout type="note" title="Reuse the parser" >}}
`SimdJsonParser` is **not thread-safe**. Reuse one instance per thread to
amortize its internal buffer allocations, rather than constructing a parser per
document.
{{< /callout >}}

## Errors

Parsing failures are subtypes of `SimdJsonException`:

{{< params >}}
JsonParsingException|SimdJsonException|Input is not valid JSON. Carries the byte `offset` of the failure.
JsonEncodingException|SimdJsonException|Input is not valid UTF-8.
{{< /params >}}
