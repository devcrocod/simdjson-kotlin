package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.OnDemandJsonIterator.IteratorResult
import io.github.devcrocod.simdjson.ResolvedClass.ResolvedClassCategory
import java.lang.reflect.Array as ReflectArray
import java.util.Collections
import java.util.LinkedList

internal class SchemaBasedJsonIterator(
    bitIndexes: BitIndexes,
    private val stringBuffer: ByteArray,
    padding: Int
) {

    private val jsonIterator = OnDemandJsonIterator(bitIndexes, padding)
    private val classResolver = ClassResolver()

    @Suppress("UNCHECKED_CAST")
    fun <T> walkDocument(padded: ByteArray, len: Int, expectedType: Class<T>): T {
        jsonIterator.init(padded, len)
        classResolver.reset()

        val resolvedExpectedClass = classResolver.resolveClass(expectedType)
        return when (resolvedExpectedClass.classCategory) {
            ResolvedClassCategory.BOOLEAN_PRIMITIVE -> jsonIterator.getRootBoolean() as T
            ResolvedClassCategory.BOOLEAN -> jsonIterator.getRootNullableBoolean() as T
            ResolvedClassCategory.BYTE_PRIMITIVE -> jsonIterator.getRootByte() as T
            ResolvedClassCategory.BYTE -> jsonIterator.getRootNullableByte() as T
            ResolvedClassCategory.SHORT_PRIMITIVE -> jsonIterator.getRootShort() as T
            ResolvedClassCategory.SHORT -> jsonIterator.getRootNullableShort() as T
            ResolvedClassCategory.INT_PRIMITIVE -> jsonIterator.getRootInt() as T
            ResolvedClassCategory.INT -> jsonIterator.getRootNullableInt() as T
            ResolvedClassCategory.LONG_PRIMITIVE -> jsonIterator.getRootLong() as T
            ResolvedClassCategory.LONG -> jsonIterator.getRootNullableLong() as T
            ResolvedClassCategory.FLOAT_PRIMITIVE -> jsonIterator.getRootFloat() as T
            ResolvedClassCategory.FLOAT -> jsonIterator.getRootNullableFloat() as T
            ResolvedClassCategory.DOUBLE_PRIMITIVE -> jsonIterator.getRootDouble() as T
            ResolvedClassCategory.DOUBLE -> jsonIterator.getRootNullableDouble() as T
            ResolvedClassCategory.CHAR_PRIMITIVE -> jsonIterator.getRootChar() as T
            ResolvedClassCategory.CHAR -> jsonIterator.getRootNullableChar() as T
            ResolvedClassCategory.STRING -> getRootString() as T
            ResolvedClassCategory.ARRAY -> getRootArray(resolvedExpectedClass.elementClass!!) as T
            ResolvedClassCategory.CUSTOM -> getRootObject(resolvedExpectedClass) as T
            ResolvedClassCategory.LIST -> throw JsonParsingException("Lists at the root are not supported. Consider using an array instead.")
        }
    }

    // region Object

    private fun getRootObject(expectedClass: ResolvedClass): Any? {
        val result = jsonIterator.startIteratingRootObject()
        val obj = getObject(expectedClass, result)
        jsonIterator.assertNoMoreJsonValues()
        return obj
    }

    private fun getObject(expectedClass: ResolvedClass): Any? {
        val result = jsonIterator.startIteratingObject()
        return getObject(expectedClass, result)
    }

    private fun getObject(expectedClass: ResolvedClass, result: IteratorResult): Any? {
        if (result == IteratorResult.NOT_EMPTY) {
            val argumentsMap = expectedClass.argumentsMap!!
            val args = arrayOfNulls<Any>(argumentsMap.argumentCount)
            val parentDepth = jsonIterator.getDepth() - 1
            collectArguments(argumentsMap, args)
            jsonIterator.skipChild(parentDepth)
            return createObject(expectedClass, args)
        } else if (result == IteratorResult.EMPTY) {
            val argumentsMap = expectedClass.argumentsMap!!
            val args = arrayOfNulls<Any>(argumentsMap.argumentCount)
            return createObject(expectedClass, args)
        }
        return null
    }

    private fun createObject(expectedClass: ResolvedClass, args: Array<Any?>): Any {
        try {
            return expectedClass.constructor!!.newInstance(*args)
        } catch (e: Exception) {
            throw JsonParsingException("Failed to construct an instance of ${expectedClass.rawClass.name}", -1, e)
        }
    }

    private fun collectArguments(argumentsMap: ConstructorArgumentsMap, args: Array<Any?>) {
        var collected = 0
        val argLen = args.size
        var hasFields = true
        while (collected < argLen && hasFields) {
            val fieldNameLen = jsonIterator.getFieldName(stringBuffer)
            jsonIterator.moveToFieldValue()
            val argument = argumentsMap.get(stringBuffer, fieldNameLen)
            if (argument != null) {
                collectArgument(argument.resolvedClass, args, argument)
                collected++
            } else {
                jsonIterator.skipChild()
            }
            hasFields = jsonIterator.nextObjectField()
        }
    }

    private fun collectArgument(argumentClass: ResolvedClass, args: Array<Any?>, argument: ConstructorArgument) {
        args[argument.idx] = when (argumentClass.classCategory) {
            ResolvedClassCategory.BOOLEAN_PRIMITIVE -> jsonIterator.getBoolean()
            ResolvedClassCategory.BOOLEAN -> jsonIterator.getNullableBoolean()
            ResolvedClassCategory.BYTE_PRIMITIVE -> jsonIterator.getByte()
            ResolvedClassCategory.BYTE -> jsonIterator.getNullableByte()
            ResolvedClassCategory.SHORT_PRIMITIVE -> jsonIterator.getShort()
            ResolvedClassCategory.SHORT -> jsonIterator.getNullableShort()
            ResolvedClassCategory.INT_PRIMITIVE -> jsonIterator.getInt()
            ResolvedClassCategory.INT -> jsonIterator.getNullableInt()
            ResolvedClassCategory.LONG_PRIMITIVE -> jsonIterator.getLong()
            ResolvedClassCategory.LONG -> jsonIterator.getNullableLong()
            ResolvedClassCategory.FLOAT_PRIMITIVE -> jsonIterator.getFloat()
            ResolvedClassCategory.FLOAT -> jsonIterator.getNullableFloat()
            ResolvedClassCategory.DOUBLE_PRIMITIVE -> jsonIterator.getDouble()
            ResolvedClassCategory.DOUBLE -> jsonIterator.getNullableDouble()
            ResolvedClassCategory.CHAR_PRIMITIVE -> jsonIterator.getChar()
            ResolvedClassCategory.CHAR -> jsonIterator.getNullableChar()
            ResolvedClassCategory.STRING -> getString()
            ResolvedClassCategory.ARRAY -> getArray(argumentClass.elementClass!!)
            ResolvedClassCategory.LIST -> getList(argumentClass.elementClass!!)
            ResolvedClassCategory.CUSTOM -> getObject(argument.resolvedClass)
        }
    }

    // endregion

    // region List

    private fun getList(elementType: ResolvedClass): List<Any?>? {
        val result = jsonIterator.startIteratingArray()
        if (result == IteratorResult.EMPTY) return Collections.emptyList()
        if (result == IteratorResult.NULL) return null

        val list = LinkedList<Any?>()
        when (elementType.classCategory) {
            ResolvedClassCategory.BOOLEAN -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableBoolean()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.BYTE -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableByte()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.CHAR -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableChar()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.SHORT -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableShort()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.INT -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableInt()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.LONG -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableLong()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.DOUBLE -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableDouble()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.FLOAT -> {
                var hasElements = true
                while (hasElements) {
                    list.add(jsonIterator.getNullableFloat()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.STRING -> {
                var hasElements = true
                while (hasElements) {
                    list.add(getString()); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.CUSTOM -> {
                var hasElements = true
                while (hasElements) {
                    list.add(getObject(elementType)); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.ARRAY -> {
                var hasElements = true
                while (hasElements) {
                    list.add(getArray(elementType.elementClass!!)); hasElements = jsonIterator.nextArrayElement()
                }
            }

            ResolvedClassCategory.LIST -> {
                var hasElements = true
                while (hasElements) {
                    list.add(getList(elementType.elementClass!!)); hasElements = jsonIterator.nextArrayElement()
                }
            }

            else -> throw JsonParsingException("Unsupported array element type: ${elementType.rawClass.name}")
        }
        return list
    }

    // endregion

    // region Array

    private fun getRootArray(elementType: ResolvedClass): Any? {
        val result = jsonIterator.startIteratingRootArray()
        val array = getArray(elementType, result)
        jsonIterator.assertNoMoreJsonValues()
        return array
    }

    private fun getArray(elementType: ResolvedClass): Any? {
        val result = jsonIterator.startIteratingArray()
        return getArray(elementType, result)
    }

    private fun getArray(elementType: ResolvedClass, result: IteratorResult): Any? {
        if (result == IteratorResult.EMPTY) {
            val category = elementType.classCategory
            return category.emptyArray ?: ReflectArray.newInstance(elementType.rawClass, 0)
        }
        if (result == IteratorResult.NULL) return null

        return when (elementType.classCategory) {
            ResolvedClassCategory.BOOLEAN_PRIMITIVE -> getPrimitiveBooleanArray()
            ResolvedClassCategory.BOOLEAN -> getBooleanArray()
            ResolvedClassCategory.BYTE_PRIMITIVE -> getPrimitiveByteArray()
            ResolvedClassCategory.BYTE -> getByteArray()
            ResolvedClassCategory.CHAR_PRIMITIVE -> getPrimitiveCharArray()
            ResolvedClassCategory.CHAR -> getCharArray()
            ResolvedClassCategory.SHORT_PRIMITIVE -> getPrimitiveShortArray()
            ResolvedClassCategory.SHORT -> getShortArray()
            ResolvedClassCategory.INT_PRIMITIVE -> getPrimitiveIntArray()
            ResolvedClassCategory.INT -> getIntArray()
            ResolvedClassCategory.LONG_PRIMITIVE -> getPrimitiveLongArray()
            ResolvedClassCategory.LONG -> getLongArray()
            ResolvedClassCategory.DOUBLE_PRIMITIVE -> getPrimitiveDoubleArray()
            ResolvedClassCategory.DOUBLE -> getDoubleArray()
            ResolvedClassCategory.FLOAT_PRIMITIVE -> getPrimitiveFloatArray()
            ResolvedClassCategory.FLOAT -> getFloatArray()
            ResolvedClassCategory.STRING -> getStringArray()
            ResolvedClassCategory.CUSTOM -> getCustomObjectArray(elementType)
            ResolvedClassCategory.ARRAY -> getArrayOfArrays(elementType)
            ResolvedClassCategory.LIST -> throw JsonParsingException("Arrays of lists are not supported.")
        }
    }

    // Primitive arrays

    private fun getPrimitiveBooleanArray(): BooleanArray {
        var array = BooleanArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getBoolean()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveByteArray(): ByteArray {
        var array = ByteArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getByte()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveCharArray(): CharArray {
        var array = CharArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getChar()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveShortArray(): ShortArray {
        var array = ShortArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getShort()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveIntArray(): IntArray {
        var array = IntArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getInt()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveLongArray(): LongArray {
        var array = LongArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getLong()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveDoubleArray(): DoubleArray {
        var array = DoubleArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getDouble()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getPrimitiveFloatArray(): FloatArray {
        var array = FloatArray(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getFloat()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    // Boxed arrays

    private fun getBooleanArray(): Array<Boolean?> {
        var array = arrayOfNulls<Boolean>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableBoolean()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getByteArray(): Array<Byte?> {
        var array = arrayOfNulls<Byte>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableByte()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getCharArray(): Array<Char?> {
        var array = arrayOfNulls<Char>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableChar()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getShortArray(): Array<Short?> {
        var array = arrayOfNulls<Short>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableShort()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getIntArray(): Array<Int?> {
        var array = arrayOfNulls<Int>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableInt()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getLongArray(): Array<Long?> {
        var array = arrayOfNulls<Long>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableLong()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getDoubleArray(): Array<Double?> {
        var array = arrayOfNulls<Double>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableDouble()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getFloatArray(): Array<Float?> {
        var array = arrayOfNulls<Float>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = jsonIterator.getNullableFloat()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    private fun getStringArray(): Array<String?> {
        var array = arrayOfNulls<String>(INITIAL_ARRAY_SIZE)
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) array = array.copyOf(calculateNewCapacity(array.size))
            array[size++] = getString()
            hasElements = jsonIterator.nextArrayElement()
        }
        return if (size != array.size) array.copyOf(size) else array
    }

    // Object and nested arrays

    @Suppress("UNCHECKED_CAST")
    private fun getCustomObjectArray(elementType: ResolvedClass): Array<Any?> {
        var array = ReflectArray.newInstance(elementType.rawClass, INITIAL_ARRAY_SIZE) as Array<Any?>
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) {
                val newArray =
                    ReflectArray.newInstance(elementType.rawClass, calculateNewCapacity(array.size)) as Array<Any?>
                System.arraycopy(array, 0, newArray, 0, array.size)
                array = newArray
            }
            array[size++] = getObject(elementType)
            hasElements = jsonIterator.nextArrayElement()
        }
        if (size != array.size) {
            val copy = ReflectArray.newInstance(elementType.rawClass, size) as Array<Any?>
            System.arraycopy(array, 0, copy, 0, size)
            return copy
        }
        return array
    }

    @Suppress("UNCHECKED_CAST")
    private fun getArrayOfArrays(elementType: ResolvedClass): Array<Any?> {
        var array = ReflectArray.newInstance(elementType.rawClass, INITIAL_ARRAY_SIZE) as Array<Any?>
        var size = 0
        var hasElements = true
        while (hasElements) {
            if (size == array.size) {
                val newArray =
                    ReflectArray.newInstance(elementType.rawClass, calculateNewCapacity(array.size)) as Array<Any?>
                System.arraycopy(array, 0, newArray, 0, array.size)
                array = newArray
            }
            array[size++] = getArray(elementType.elementClass!!)
            hasElements = jsonIterator.nextArrayElement()
        }
        if (size != array.size) {
            val copy = ReflectArray.newInstance(elementType.rawClass, size) as Array<Any?>
            System.arraycopy(array, 0, copy, 0, size)
            return copy
        }
        return array
    }

    // endregion

    // region String

    private fun getString(): String? {
        val len = jsonIterator.getString(stringBuffer)
        if (len == -1) return null
        return String(stringBuffer, 0, len, Charsets.UTF_8)
    }

    private fun getRootString(): String? {
        val len = jsonIterator.getRootString(stringBuffer)
        if (len == -1) return null
        return String(stringBuffer, 0, len, Charsets.UTF_8)
    }

    // endregion

    companion object {
        private const val INITIAL_ARRAY_SIZE = 16

        private fun calculateNewCapacity(oldCapacity: Int): Int {
            val minCapacity = oldCapacity + 1
            val newCapacity = oldCapacity + (oldCapacity shr 1)
            return if (newCapacity < minCapacity) minCapacity else newCapacity
        }
    }
}
