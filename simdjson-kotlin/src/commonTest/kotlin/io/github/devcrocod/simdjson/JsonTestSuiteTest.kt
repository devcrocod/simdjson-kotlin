package io.github.devcrocod.simdjson

import io.kotest.assertions.withClue
import kotlin.test.Test
import kotlin.test.fail

/**
 * JSONTestSuite compliance tests.
 *
 * Tests the parser against the JSONTestSuite minefield files from the simdjson-data repository.
 * Each file is classified by its prefix:
 * - `y_` — valid JSON, must parse successfully
 * - `n_` — invalid JSON, must fail to parse
 * - `i_` — implementation-defined behavior, fixed expectations for cross-platform consistency
 *
 * Test data: `testdata/simdjson-data/jsonchecker/minefield/`
 */
class JsonTestSuiteTest {

    private val minefieldDir = "simdjson-data/jsonchecker/minefield"

    @Test
    fun `JSONTestSuite - y_ files must parse successfully`() {
        val files = listMinefieldFiles("y_")
        if (files.isEmpty()) {
            skipWithMessage()
            return
        }

        val failures = mutableListOf<String>()
        for (name in files) {
            val bytes = TestResources.load("$minefieldDir/$name")
            try {
                SimdJsonParser().parse(bytes, bytes.size)
            } catch (e: Exception) {
                failures.add("$name: ${e.message}")
            }
        }
        withClue("y_ files that should parse but failed:\n${failures.joinToString("\n")}") {
            if (failures.isNotEmpty()) fail("${failures.size}/${files.size} y_ files failed")
        }
    }

    @Test
    fun `JSONTestSuite - n_ files must fail to parse`() {
        val files = listMinefieldFiles("n_")
        if (files.isEmpty()) {
            skipWithMessage()
            return
        }

        val failures = mutableListOf<String>()
        for (name in files) {
            val bytes = TestResources.load("$minefieldDir/$name")
            try {
                SimdJsonParser().parse(bytes, bytes.size)
                failures.add(name)
            } catch (_: SimdJsonException) {
                // expected
            }
        }
        withClue("n_ files that should fail but parsed successfully:\n${failures.joinToString("\n")}") {
            if (failures.isNotEmpty()) fail("${failures.size}/${files.size} n_ files passed unexpectedly")
        }
    }

    @Test
    fun `JSONTestSuite - i_ files must match expected classification`() {
        val files = listMinefieldFiles("i_")
        if (files.isEmpty()) {
            skipWithMessage()
            return
        }

        val unexpected = mutableListOf<String>()
        for (name in files) {
            val bytes = TestResources.load("$minefieldDir/$name")
            val parsed = try {
                SimdJsonParser().parse(bytes, bytes.size)
                true
            } catch (_: SimdJsonException) {
                false
            }

            val expected = name in I_FILES_EXPECTED_TO_PASS
            if (parsed != expected) {
                unexpected.add("$name: expected ${if (expected) "pass" else "fail"}, got ${if (parsed) "pass" else "fail"}")
            }
        }
        withClue("i_ files with unexpected results:\n${unexpected.joinToString("\n")}") {
            if (unexpected.isNotEmpty()) fail("${unexpected.size}/${files.size} i_ files mismatched")
        }
    }

    private fun listMinefieldFiles(prefix: String): List<String> {
        return try {
            TestResources.listFiles(minefieldDir)
                .filter { it.startsWith(prefix) && it.endsWith(".json") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun skipWithMessage() {
        println(
            "Skipping JSONTestSuite tests: no test files found. " +
                "Clone https://github.com/simdjson/simdjson-data into testdata/simdjson-data"
        )
    }

    companion object {
        /**
         * i_ files that simdjson-kotlin successfully parses on this platform.
         * JVM and Native differ in number overflow handling and BOM tolerance.
         */
        val I_FILES_EXPECTED_TO_PASS: Set<String> get() = platformIFilesExpectedToPass()
    }
}
