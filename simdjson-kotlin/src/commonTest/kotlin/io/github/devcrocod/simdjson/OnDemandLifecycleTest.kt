package io.github.devcrocod.simdjson

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnDemandLifecycleTest {

    @Test
    fun useBlockOnDocument() {
        val parser = SimdJsonParser()
        parser.iterate("""{"x": 42}""").use { doc ->
            val obj = doc.getObject()
            obj["x"].getLong() shouldBe 42L
        }
        parser.close()
    }

    @Test
    fun useBlockOnValue() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"name": "test"}""")
        doc.getObject()["name"].use { value ->
            value.getString() shouldBe "test"
        }
        doc.close()
        parser.close()
    }

    @Test
    fun useBlockOnObject() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"a": 1, "b": 2}""")
        doc.getObject().use { obj ->
            obj["a"].getLong() shouldBe 1L
            obj["b"].getLong() shouldBe 2L
        }
        doc.close()
        parser.close()
    }

    @Test
    fun documentCloseCleanupChildren() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"field1": 10, "field2": {"nested": 20}}""")
        val obj = doc.getObject()
        val field1 = obj["field1"]
        val field2 = obj["field2"]
        val nestedObj = field2.getObject()
        val nestedValue = nestedObj["nested"]

        // Don't explicitly close any children — let doc.close() handle it
        doc.close()
        // Should not crash

        parser.close()
    }

    @Test
    fun doubleCloseIsIdempotent() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""{"x": 5}""")
        val obj = doc.getObject()
        val value = obj["x"]

        // Close children
        value.close()
        obj.close()

        // Close document twice
        doc.close()
        doc.close()  // Second close should not crash

        parser.close()
    }

    @Test
    fun nestedUseBlocks() {
        val parser = SimdJsonParser()
        parser.iterate(
            """{"data": {"name": "alice", "age": 30}}"""
        ).use { doc ->
            doc.getObject().use { rootObj ->
                rootObj["data"].use { dataValue ->
                    dataValue.getObject().use { dataObj ->
                        dataObj["name"].use { nameValue ->
                            nameValue.getString() shouldBe "alice"
                        }
                        dataObj["age"].use { ageValue ->
                            ageValue.getLong() shouldBe 30L
                        }
                    }
                }
            }
        }
        parser.close()
    }
}
