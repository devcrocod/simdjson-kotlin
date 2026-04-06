package io.github.devcrocod.simdjson

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import kotlin.test.Test

/**
 * Tests the double parser against the parse-number-fxx-test-data suite.
 *
 * To run these tests, clone the test data into `testdata/`:
 * ```
 * cd simdjson-kotlin
 * git clone https://github.com/nigeltao/parse-number-fxx-test-data testdata/parse-number-fxx-test-data
 * ```
 *
 * The test data directory can also be configured via the system property `org.simdjson.testdata.dir`.
 */
class FloatingPointTestDataTest {

    private val parser = NumberParser()

    @Test
    fun `parse-number-fxx-test-data - double`() {
        val testFiles = listTestFiles()
        if (testFiles.isEmpty()) {
            println(
                "Skipping parse-number-fxx-test-data tests: no test files found. " +
                    "Clone https://github.com/nigeltao/parse-number-fxx-test-data into testdata/"
            )
            return
        }
        for (file in testFiles) {
            BufferedReader(FileReader(file)).use { reader ->
                var lineNo = 0
                var line = reader.readLine()
                while (line != null) {
                    lineNo++
                    val testCase = parseFxxLine(line)
                    val input = testCase.input
                    val buf = "$input ".toByteArray()
                    withClue("${file.name}:$lineNo input=$input") {
                        parser.parseDouble(buf, buf.size, 0) shouldBe testCase.expectedDouble
                    }
                    line = reader.readLine()
                }
            }
        }
    }

    @Test
    fun `parse-number-fxx-test-data - float`() {
        val testFiles = listTestFiles()
        if (testFiles.isEmpty()) {
            println(
                "Skipping parse-number-fxx-test-data tests: no test files found. " +
                    "Clone https://github.com/nigeltao/parse-number-fxx-test-data into testdata/"
            )
            return
        }
        for (file in testFiles) {
            BufferedReader(FileReader(file)).use { reader ->
                var lineNo = 0
                var line = reader.readLine()
                while (line != null) {
                    lineNo++
                    val testCase = parseFxxLine(line)
                    val input = testCase.input
                    val buf = "$input ".toByteArray()
                    withClue("${file.name}:$lineNo input=$input") {
                        parser.parseFloat(buf, buf.size, 0) shouldBe testCase.expectedFloat
                    }
                    line = reader.readLine()
                }
            }
        }
    }

    private fun listTestFiles(): List<File> {
        val testDataDir = System.getProperty(
            "org.simdjson.testdata.dir",
            System.getProperty("user.dir") + "/testdata"
        )
        val dataDir = Path.of(testDataDir, "parse-number-fxx-test-data", "data").toFile()
        return dataDir.listFiles()
            ?.filter { it.isFile }
            ?.sorted()
            ?: emptyList()
    }

    private fun parseFxxLine(line: String): FxxTestCase {
        val cells = line.split(" ")
        val expectedFloat = java.lang.Float.intBitsToFloat(java.lang.Long.decode("0x" + cells[1]).toInt())
        val expectedDouble = java.lang.Double.longBitsToDouble(java.lang.Long.decode("0x" + cells[2]))
        val input = normalizeInput(cells[3])
        return FxxTestCase(input, expectedFloat, expectedDouble)
    }

    /**
     * Normalizes the input number to be a valid JSON floating-point number.
     * The fxx format may have numbers like `.5`, `1.e2`, or plain integers `123`.
     * JSON requires at least one digit before the decimal point, a digit after the decimal point,
     * and a decimal point or exponent to be a floating-point number.
     */
    private fun normalizeInput(input: String): String {
        var s = input
        val isDouble = 'e' in s || 'E' in s || '.' in s
        if (isDouble) {
            if (s.startsWith(".")) {
                s = "0$s"
            }
            s = s.replace(Regex("\\.[eE]"), ".0e")
            return s
        }
        return "$s.0"
    }

    private data class FxxTestCase(
        val input: String,
        val expectedFloat: Float,
        val expectedDouble: Double,
    )
}
