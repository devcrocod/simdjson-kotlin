package io.github.devcrocod.simdjson

import io.kotest.matchers.shouldBe
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
}
