---
title: On-Demand API
description: Lazy, forward-only iteration — decode values only when accessed, without building a tree.
weight: 20
---

The On-Demand API decodes values lazily as you touch them. Nothing builds a full
tree, so it's faster and allocates far less than the [DOM](../dom/) — at the
cost of forward-only access.

## Iterating

`iterate` returns a `JsonDocument`. Walk it in document order:

```kotlin
val json: ByteArray = loadTwitterJson()

SimdJsonParser().use { parser ->
    parser.iterate(json).use { doc ->
        for (tweet in doc.getObject()["statuses"].getArray()) {
            val user = tweet.getObject()["user"].getObject()
            if (user["default_profile"].getBoolean()) {
                println(user["screen_name"].getString())
            }
        }
    }
}
```

## Reading values

Navigate with `getObject()` / `getArray()`, then pull scalars with the typed
getters:

{{< params >}}
JsonDocument|AutoCloseable|Root value: `getObject()`, `getArray()`, `getString()`, `getLong()`, `getULong()`, `getDouble()`, `getBoolean()`, `isNull()`, `getType()`.
OnDemandObject|Iterable<OnDemandField>|Forward-only object. `obj["field"]` and `obj.findField("field")` both return an `OnDemandValue`.
OnDemandArray|Iterable<OnDemandValue>|Forward-only array — iterate to read its elements.
OnDemandValue|AutoCloseable|A not-yet-decoded value: the same typed getters as `JsonDocument`, plus `materialize()`.
OnDemandField|—|A single object entry: `name` and `value`.
{{< /params >}}

Need a random-access snapshot of a subtree? `OnDemandValue.materialize()` decodes
it into a DOM [`JsonValue`](../dom/#the-jsonvalue-tree):

```kotlin
val user: JsonValue = tweet.getObject()["user"].materialize()
```

## Forward-only rules

On-Demand trades random access for speed. Three rules follow from that:

{{< callout type="important" title="Consume in order, once" >}}
- Read fields and elements in the order they appear in the document.
- An object or array is **consumed by iterating it** — iterate it once.
- Finish with a value before moving on to its sibling.

Breaking these throws a `JsonIterationException`. When a typed getter meets the
wrong JSON type (e.g. `getString()` on a number) you get a `JsonTypeException`
carrying the `expected` and `actual` types.
{{< /callout >}}

## Lifecycle

The `JsonDocument`, and every value it yields, live on the parser's buffers — so
close the document when you're done. `iterate(json).use { doc -> … }` does that,
and closing the parser closes everything under it. Both are `AutoCloseable`.

## DOM or On-Demand?

| Reach for… | When |
|---|---|
| **On-Demand** | You read each field once, in order, and want the lowest latency and allocation. |
| **DOM** | You need random access, multiple passes, or want to keep values after the parser closes. |
| **[Serialization](../serialization/)** | You're on the JVM and want to decode straight into `@Serializable` classes. |
