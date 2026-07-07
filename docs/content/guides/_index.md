---
title: Guides
description: The three ways to read JSON with simdjson-kotlin — DOM, On-Demand, and kotlinx.serialization.
weight: 20
params:
  icon: book-open
---

simdjson-kotlin gives you three ways to read JSON, from a full random-access
tree down to zero-copy streaming. Pick the one that matches how you consume the
data.

{{< card-grid >}}
{{< card title="DOM API" href="dom/" >}}
Parse the whole document into an immutable `JsonValue` tree with random access.
{{< /card >}}
{{< card title="On-Demand API" href="on-demand/" >}}
Lazy, forward-only iteration — the fastest, lowest-allocation path.
{{< /card >}}
{{< card title="kotlinx.serialization" href="serialization/" >}}
Decode straight into your `@Serializable` classes on the JVM.
{{< /card >}}
{{< /card-grid >}}
