package io.github.devcrocod.simdjson

internal actual fun platformIFilesExpectedToPass(): Set<String> =
    if (BackendSelector.useJni) JNI_I_FILES_EXPECTED_TO_PASS else VECTOR_I_FILES_EXPECTED_TO_PASS

// Vector backend (Kotlin number parser): tolerates huge exponents / overflow as Infinity/0.0.
private val VECTOR_I_FILES_EXPECTED_TO_PASS = setOf(
    "i_number_double_huge_neg_exp.json",
    "i_number_huge_exp.json",
    "i_number_neg_int_huge_exp.json",
    "i_number_pos_double_huge_exp.json",
    "i_number_real_neg_overflow.json",
    "i_number_real_pos_overflow.json",
    "i_number_real_underflow.json",
    "i_structure_500_nested_arrays.json",
)

// JNI backend delegates to C++ simdjson — same classification as the Native targets.
private val JNI_I_FILES_EXPECTED_TO_PASS = setOf(
    "i_number_double_huge_neg_exp.json",
    "i_number_real_underflow.json",
    "i_structure_500_nested_arrays.json",
    "i_structure_UTF-8_BOM_empty_object.json",
)
