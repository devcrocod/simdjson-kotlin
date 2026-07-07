---
title: Quickstart
description: Parse your first JSON document with the DOM and On-Demand APIs.
weight: 20
---

Create a `SimdJsonParser`, parse some JSON, and read values out of the result.
The parser holds native resources on some backends, so always close it — the
idiomatic way is Kotlin's [`use`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.io/use.html).

## DOM: parse into a tree

`parse` reads the whole document into an immutable tree of `JsonValue`s:

```kotlin
import io.github.devcrocod.simdjson.*

val json = """{ "name": "simdjson-kotlin", "stars": 42, "fast": true }"""

SimdJsonParser().use { parser ->
    val root = parser.parse(json) as JsonObject

    println((root["name"] as JsonString).value)   // simdjson-kotlin
    println((root["stars"] as JsonNumber).toLong()) // 42
    println((root["fast"] as JsonBoolean).value)    // true
}
```

## On-Demand: decode only what you touch

`iterate` returns a lazy, forward-only document — values are decoded only when
you access them, and nothing builds a full tree:

```kotlin
SimdJsonParser().use { parser ->
    parser.iterate(json).use { doc ->
        val root = doc.getObject()
        println(root["name"].getString())    // simdjson-kotlin
        println(root["stars"].getLong())      // 42
        println(root["fast"].getBoolean())    // true
    }
}
```

{{< callout type="note" title="Which API should I use?" >}}
Reach for the [DOM API](../../guides/dom/) when you need random access or want
to keep the parsed values around. Reach for the
[On-Demand API](../../guides/on-demand/) when you read each field once, in order
— it's faster and allocates far less. On the JVM you can also decode straight
into `@Serializable` classes with the
[serialization module](../../guides/serialization/).
{{< /callout >}}
