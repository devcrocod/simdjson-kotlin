package io.github.devcrocod.simdjson

internal actual fun platformIFilesExpectedToPass(): Set<String> = setOf(
    // Number overflow/underflow — JVM handles these via its number parser
    "i_number_double_huge_neg_exp.json",
    "i_number_huge_exp.json",
    "i_number_neg_int_huge_exp.json",
    "i_number_pos_double_huge_exp.json",
    "i_number_real_neg_overflow.json",
    "i_number_real_pos_overflow.json",
    "i_number_real_underflow.json",
    // Structural
    "i_structure_500_nested_arrays.json",
)
