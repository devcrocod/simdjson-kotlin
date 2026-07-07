---
title: Home
description: A Kotlin Multiplatform JSON parser based on simdjson — parse gigabytes of JSON per second.
---

{{< hero title="Fast JSON parsing for Kotlin Multiplatform." gradient="for Kotlin Multiplatform." subtitle="simdjson-kotlin is a Kotlin Multiplatform port of simdjson — the JSON parser that uses SIMD instructions to parse gigabytes of JSON per second. Three APIs (DOM, On-Demand, kotlinx.serialization) across JVM, Android, Native and iOS." >}}
<a class="kt-button kt-button--primary kt-button--lg" href="getting-started/">Get started</a>
<a class="kt-button kt-button--ghost kt-button--lg" href="guides/on-demand/">On-Demand API</a>
{{< /hero >}}

## Explore

{{< feature-grid >}}
{{< card title="Getting started" href="getting-started/" icon="rocket" >}}
Add the dependency, check the requirements, and parse your first document.
{{< /card >}}
{{< card title="DOM API" href="guides/dom/" icon="layers" >}}
Parse a whole document into an immutable in-memory tree of `JsonValue`s.
{{< /card >}}
{{< card title="On-Demand API" href="guides/on-demand/" icon="code" >}}
Lazy, forward-only iteration — decode values only when accessed, no tree built.
{{< /card >}}
{{< card title="kotlinx.serialization" href="guides/serialization/" icon="puzzle" >}}
Decode `@Serializable` classes directly from JSON on the JVM.
{{< /card >}}
{{< card title="Examples" href="examples/" icon="book-open" >}}
Worked end-to-end examples comparing the DOM, On-Demand and typed paths.
{{< /card >}}
{{< /feature-grid >}}
