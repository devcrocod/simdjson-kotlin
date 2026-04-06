package io.github.devcrocod.simdjson

internal actual fun platformIFilesExpectedToPass(): Set<String> = setOf(
    // These three pass on both JVM and Native
    "i_number_double_huge_neg_exp.json",
    "i_number_real_underflow.json",
    "i_structure_500_nested_arrays.json",
    // Native parses BOM-prefixed objects where JVM does not
    "i_structure_UTF-8_BOM_empty_object.json",
)
