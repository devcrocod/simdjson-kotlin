package io.github.devcrocod.simdjson

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class BackendSelectorTest {

    @Test
    fun `backend selection honors environment`() {
        when (System.getProperty("simdjson.backend")) {
            "jni" -> BackendSelector.useJni shouldBe true
            "vector" -> BackendSelector.useJni shouldBe false
            else -> {
                if (Runtime.version().feature() >= 24) {
                    BackendSelector.useJni shouldBe false
                }
            }
        }
    }

    @Test
    fun `forced jni selects jni without hint`() {
        val decision = BackendSelector.decide("jni", 24, vectorModulePresent = true) { true }
        decision.useJni shouldBe true
        decision.hint.shouldBeNull()
    }

    @Test
    fun `forced vector selects vector without hint`() {
        val decision = BackendSelector.decide("vector", 24, vectorModulePresent = false) { false }
        decision.useJni shouldBe false
        decision.hint.shouldBeNull()
    }

    @Test
    fun `jdk below 24 selects jni without hint`() {
        val decision = BackendSelector.decide(null, 23, vectorModulePresent = true) { true }
        decision.useJni shouldBe true
        decision.hint.shouldBeNull()
    }

    @Test
    fun `missing vector module selects jni with add-modules hint`() {
        val decision = BackendSelector.decide(null, 24, vectorModulePresent = false) { true }
        decision.useJni shouldBe true
        decision.hint.shouldNotBeNull() shouldContain "--add-modules jdk.incubator.vector"
    }

    @Test
    fun `unusable vector api selects jni with fallback hint`() {
        val decision = BackendSelector.decide(null, 24, vectorModulePresent = true) { false }
        decision.useJni shouldBe true
        decision.hint.shouldNotBeNull() shouldContain "falling back to the JNI backend"
    }

    @Test
    fun `jdk 24 with usable vector selects vector without hint`() {
        val decision = BackendSelector.decide(null, 24, vectorModulePresent = true) { true }
        decision.useJni shouldBe false
        decision.hint.shouldBeNull()
    }

    @Test
    fun `unrecognized forced value falls through to default logic`() {
        val decision = BackendSelector.decide("foo", 24, vectorModulePresent = true) { true }
        decision.useJni shouldBe false
        decision.hint.shouldBeNull()
    }

    @Test
    fun `vector usability is not checked when module is absent`() {
        val decision = BackendSelector.decide(null, 24, vectorModulePresent = false) { error("must not be called") }
        decision.useJni shouldBe true
    }
}
