package io.github.devcrocod.simdjson

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import jdk.incubator.vector.ByteVector
import kotlin.test.Test

class VectorSpeciesSupportTest {

    @Test
    fun `128, 256 and 512-bit species are accepted`() {
        shouldNotThrow<IllegalArgumentException> {
            VectorUtils.assertSupportForSpecies(ByteVector.SPECIES_128)
            VectorUtils.assertSupportForSpecies(ByteVector.SPECIES_256)
            VectorUtils.assertSupportForSpecies(ByteVector.SPECIES_512)
        }
    }

    @Test
    fun `species below 128-bit is rejected`() {
        shouldThrow<IllegalArgumentException> {
            VectorUtils.assertSupportForSpecies(ByteVector.SPECIES_64)
        }
    }
}
