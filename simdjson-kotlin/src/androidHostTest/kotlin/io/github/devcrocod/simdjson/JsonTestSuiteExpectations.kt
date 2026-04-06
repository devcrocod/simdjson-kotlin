package io.github.devcrocod.simdjson

internal actual fun platformIFilesExpectedToPass(): Set<String> = setOf(
    // Android uses C++ simdjson via JNI (same as native targets)
    "i_number_double_huge_neg_exp.json",
    "i_number_real_underflow.json",
    "i_structure_500_nested_arrays.json",
    // C++ simdjson handles BOM-prefixed JSON
    "i_structure_UTF-8_BOM_empty_object.json",
)
