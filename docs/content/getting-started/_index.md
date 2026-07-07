---
title: Getting started
description: Add simdjson-kotlin to your build, check the platform requirements, and parse your first document.
weight: 10
params:
  icon: rocket
---

simdjson-kotlin is a Kotlin Multiplatform port of
[simdjson](https://github.com/simdjson/simdjson), a JSON parser that uses SIMD
instructions to parse gigabytes of JSON per second, based on the paper
[Parsing Gigabytes of JSON per Second](https://arxiv.org/abs/1902.08318). The
JVM implementation is based on
[simdjson-java](https://github.com/simdjson/simdjson-java).

{{< callout type="warning" title="Early development" >}}
simdjson-kotlin is in early development. Following [SemVer](https://semver.org/),
a major version of zero means initial development, so the API should not be
considered stable.
{{< /callout >}}

## What's next

{{< card-grid >}}
{{< card title="Installation" href="installation/" >}}
Gradle coordinates, JVM/Android/Native requirements, and the supported-platform matrix.
{{< /card >}}
{{< card title="Quickstart" href="quickstart/" >}}
Parse your first JSON document in a few lines.
{{< /card >}}
{{< /card-grid >}}
