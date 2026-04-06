package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TypeExceptionTest {

    @Test
    fun getStringOnNumberReportsTypes() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("[42]")
        val value = doc.getArray().iterator().next()
        val ex = shouldThrow<JsonTypeException> { value.getString() }
        ex.expected shouldBe JsonType.STRING
        ex.actual shouldBe JsonType.NUMBER
        doc.close()
        parser.close()
    }

    @Test
    fun getLongOnStringReportsTypes() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("""["hello"]""")
        val value = doc.getArray().iterator().next()
        val ex = shouldThrow<JsonTypeException> { value.getLong() }
        ex.expected shouldBe JsonType.NUMBER
        ex.actual shouldBe JsonType.STRING
        doc.close()
        parser.close()
    }

    @Test
    fun getObjectOnArrayReportsTypes() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("[[1]]")
        val value = doc.getArray().iterator().next()
        val ex = shouldThrow<JsonTypeException> { value.getObject() }
        ex.expected shouldBe JsonType.OBJECT
        ex.actual shouldBe JsonType.ARRAY
        doc.close()
        parser.close()
    }

    @Test
    fun getBooleanOnNullReportsTypes() {
        val parser = SimdJsonParser()
        val doc = parser.iterate("[null]")
        val value = doc.getArray().iterator().next()
        val ex = shouldThrow<JsonTypeException> { value.getBoolean() }
        ex.expected shouldBe JsonType.BOOLEAN
        ex.actual shouldBe JsonType.NULL
        doc.close()
        parser.close()
    }
}
