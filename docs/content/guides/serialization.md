---
title: kotlinx.serialization
description: Decode @Serializable classes directly from JSON on the JVM.
weight: 30
---

The `simdjson-kotlin-serialization` module plugs simdjson-kotlin into
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization): decode
JSON straight into your `@Serializable` classes, with simdjson doing the parsing.

{{< callout type="warning" title="JVM only" >}}
The serialization module is currently **JVM-only**. Add it to your `jvmMain`
source set (see [Installation](../../getting-started/installation/)). The
[DOM](../dom/) and [On-Demand](../on-demand/) APIs in the core module work on
every supported target.
{{< /callout >}}

## Decoding

Annotate your models with `@Serializable`, create a `SimdJson` instance, and
decode:

```kotlin
import io.github.devcrocod.simdjson.serialization.SimdJson
import kotlinx.serialization.Serializable

@Serializable
data class Twitter(val statuses: List<Status>)

@Serializable
data class Status(val user: User)

@Serializable
data class User(val default_profile: Boolean, val screen_name: String)

val json: String = loadTwitterJson()

val simdJson = SimdJson { ignoreUnknownKeys = true }
for (status in simdJson.decodeFromString<Twitter>(json).statuses) {
    if (status.user.default_profile) {
        println(status.user.screen_name)
    }
}
```

To decode from a UTF-8 `ByteArray` (skipping the `String` step), use
`decodeFromByteArray` with an explicit serializer:

```kotlin
import kotlinx.serialization.serializer

val twitter = simdJson.decodeFromByteArray(serializer<Twitter>(), bytes)
```

## Configuration

`SimdJson { }` builds an instance via a builder, mirroring kotlinx.serialization's
`Json { }`:

{{< params >}}
ignoreUnknownKeys|Boolean|Skip JSON keys with no matching property instead of failing. Default `false`.
isLenient|Boolean|Relax the input grammar when reading values. Default `false`.
coerceInputValues|Boolean|Substitute the property default for a missing or `null`-for-non-null value. Default `false`.
serializersModule|SerializersModule|Register contextual or polymorphic serializers.
{{< /params >}}

## Decoding only

{{< callout type="important" title="Parser, not a full format" >}}
simdjson-kotlin is a **parser** — `SimdJson` decodes only. `encodeToString` throws
`UnsupportedOperationException`; use
[`kotlinx.serialization.json`](https://github.com/Kotlin/kotlinx.serialization)
for encoding. Decoding failures throw `SimdJsonDecodingException` (a
`kotlinx.serialization.SerializationException`).
{{< /callout >}}
