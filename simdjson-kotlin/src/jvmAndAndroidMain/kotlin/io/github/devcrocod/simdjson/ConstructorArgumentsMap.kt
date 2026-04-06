package io.github.devcrocod.simdjson


internal class ConstructorArgumentsMap(val argumentCount: Int) {

    private val capacity = ceilingPowerOfTwo(argumentCount)
    private val moduloMask = capacity - 1
    private val keys = arrayOfNulls<ByteArray>(capacity)
    private val arguments = arrayOfNulls<ConstructorArgument>(capacity)

    fun put(fieldName: ByteArray, argument: ConstructorArgument) {
        var place = findPlace(fieldName, fieldName.size)
        while (keys[place] != null) {
            place = (place + 1) and moduloMask
        }
        arguments[place] = argument
        keys[place] = fieldName
    }

    fun get(buffer: ByteArray, len: Int): ConstructorArgument? {
        var place = findPlace(buffer, len)
        for (i in 0 until capacity) {
            val key = keys[place] ?: return null
            if (key.size == len && key.contentEquals(buffer, 0, len)) {
                return arguments[place]
            }
            place = (place + 1) and moduloMask
        }
        return null
    }

    private fun findPlace(buffer: ByteArray, len: Int): Int {
        val hash = hash(buffer, len)
        return hash and moduloMask
    }

    companion object {
        private const val M2 = 0x7a646e4dL

        private fun ceilingPowerOfTwo(argumentCount: Int): Int {
            return 1 shl (32 - Integer.numberOfLeadingZeros(argumentCount - 1))
        }

        private fun hash(data: ByteArray, len: Int): Int {
            val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.nativeOrder())
            var h = 0L
            var i = 0
            while (i + 7 < len) {
                h = h * M2 + buf.getLong(i)
                i += 8
            }
            if (i + 3 < len) {
                h = h * M2 + buf.getInt(i)
                i += 4
            }
            while (i < len) {
                h = h * M2 + data[i]
                i++
            }
            h *= M2
            return (h xor (h ushr 32)).toInt()
        }

        private fun ByteArray.contentEquals(other: ByteArray, otherOffset: Int, length: Int): Boolean {
            if (this.size != length) return false
            for (i in 0 until length) {
                if (this[i] != other[otherOffset + i]) return false
            }
            return true
        }
    }
}
