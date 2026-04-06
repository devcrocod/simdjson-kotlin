package io.github.devcrocod.simdjson

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorShape
import jdk.incubator.vector.VectorSpecies

internal object VectorUtils {

    val INT_SPECIES: VectorSpecies<Int>
    val BYTE_SPECIES: VectorSpecies<Byte>

    init {
        val species = System.getProperty("simdjson.species", "preferred")
        when (species) {
            "preferred" -> {
                BYTE_SPECIES = ByteVector.SPECIES_PREFERRED
                INT_SPECIES = IntVector.SPECIES_PREFERRED
                assertSupportForSpecies(BYTE_SPECIES)
                assertSupportForSpecies(INT_SPECIES)
            }

            "512" -> {
                BYTE_SPECIES = ByteVector.SPECIES_512
                INT_SPECIES = IntVector.SPECIES_512
            }

            "256" -> {
                BYTE_SPECIES = ByteVector.SPECIES_256
                INT_SPECIES = IntVector.SPECIES_256
            }

            else -> throw IllegalArgumentException("Unsupported vector species: $species")
        }
    }

    private fun assertSupportForSpecies(species: VectorSpecies<*>) {
        if (species.vectorShape() != VectorShape.S_256_BIT && species.vectorShape() != VectorShape.S_512_BIT) {
            throw IllegalArgumentException("Unsupported vector species: $species")
        }
    }

    fun repeat(array: ByteArray): ByteVector {
        val n = BYTE_SPECIES.vectorByteSize() / 4
        val result = ByteArray(n * array.size)
        for (dst in result.indices step array.size) {
            array.copyInto(result, dst)
        }
        return ByteVector.fromArray(BYTE_SPECIES, result, 0)
    }
}
