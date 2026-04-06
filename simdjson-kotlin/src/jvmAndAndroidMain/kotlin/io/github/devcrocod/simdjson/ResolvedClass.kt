package io.github.devcrocod.simdjson

import io.github.devcrocod.simdjson.annotations.JsonFieldName
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.full.primaryConstructor

internal class ResolvedClass(targetType: Type, classResolver: ClassResolver) {

    val classCategory: ResolvedClassCategory
    val rawClass: Class<*>
    val elementClass: ResolvedClass?
    val constructor: Constructor<*>?
    val argumentsMap: ConstructorArgumentsMap?

    init {
        if (targetType is ParameterizedType) {
            rawClass = targetType.rawType as Class<*>
            elementClass = resolveElementClass(targetType, classResolver)
        } else {
            rawClass = targetType as Class<*>
            elementClass = resolveElementClass(rawClass, classResolver)
        }

        classCategory = resolveClassType(rawClass)
        if (classCategory == ResolvedClassCategory.CUSTOM) {
            checkIfCustomClassIsSupported(rawClass)
            constructor = rawClass.declaredConstructors[0]
            constructor.isAccessible = true
            val parameters = constructor.parameters
            val genericTypes = constructor.genericParameterTypes
            val kotlinParamNames = resolveKotlinParamNames(rawClass)
            argumentsMap = ConstructorArgumentsMap(parameters.size)
            for (i in parameters.indices) {
                val parameterType = genericTypes[i]
                val fieldName = resolveFieldName(parameters[i], rawClass, kotlinParamNames?.getOrNull(i))
                val fieldNameBytes = fieldName.toByteArray(Charsets.UTF_8)
                argumentsMap.put(fieldNameBytes, ConstructorArgument(i, classResolver.resolveClass(parameterType)))
            }
        } else {
            constructor = null
            argumentsMap = null
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    enum class ResolvedClassCategory(val javaClass: Class<*>?, val emptyArray: Any?) {
        BOOLEAN_PRIMITIVE(Boolean::class.javaPrimitiveType, BooleanArray(0)),
        BOOLEAN(java.lang.Boolean::class.java, arrayOf<Boolean>()),
        BYTE_PRIMITIVE(Byte::class.javaPrimitiveType, ByteArray(0)),
        BYTE(java.lang.Byte::class.java, arrayOf<Byte>()),
        CHAR_PRIMITIVE(Char::class.javaPrimitiveType, CharArray(0)),
        CHAR(java.lang.Character::class.java, arrayOf<Char>()),
        SHORT_PRIMITIVE(Short::class.javaPrimitiveType, ShortArray(0)),
        SHORT(java.lang.Short::class.java, arrayOf<Short>()),
        INT_PRIMITIVE(Int::class.javaPrimitiveType, IntArray(0)),
        INT(java.lang.Integer::class.java, arrayOf<Int>()),
        LONG_PRIMITIVE(Long::class.javaPrimitiveType, LongArray(0)),
        LONG(java.lang.Long::class.java, arrayOf<Long>()),
        DOUBLE_PRIMITIVE(Double::class.javaPrimitiveType, DoubleArray(0)),
        DOUBLE(java.lang.Double::class.java, arrayOf<Double>()),
        FLOAT_PRIMITIVE(Float::class.javaPrimitiveType, FloatArray(0)),
        FLOAT(java.lang.Float::class.java, arrayOf<Float>()),
        STRING(String::class.java, arrayOf<String>()),
        CUSTOM(null, null),
        ARRAY(null, null),
        LIST(List::class.java, null);
    }

    companion object {
        private fun resolveElementClass(
            parameterizedType: ParameterizedType,
            classResolver: ClassResolver
        ): ResolvedClass {
            if (parameterizedType.rawType != List::class.java) {
                throw JsonParsingException("Parametrized types other than java.util.List are not supported.")
            }
            return classResolver.resolveClass(parameterizedType.actualTypeArguments[0])
        }

        private fun resolveElementClass(cls: Class<*>, classResolver: ClassResolver): ResolvedClass? {
            if (cls == List::class.java) {
                throw JsonParsingException("Undefined list element type.")
            }
            val componentType = cls.componentType
            return if (componentType != null) classResolver.resolveClass(componentType) else null
        }

        private fun resolveClassType(cls: Class<*>): ResolvedClassCategory {
            if (Iterable::class.java.isAssignableFrom(cls) && cls != List::class.java) {
                throw JsonParsingException(
                    "Unsupported class: ${cls.name}. For JSON arrays at the root, use Java arrays. " +
                        "For inner JSON arrays, use either Java arrays or java.util.List."
                )
            }
            if (cls.isArray) {
                return ResolvedClassCategory.ARRAY
            }
            for (category in ResolvedClassCategory.entries) {
                if (category.javaClass == cls) {
                    return category
                }
            }
            return ResolvedClassCategory.CUSTOM
        }

        private fun checkIfCustomClassIsSupported(cls: Class<*>) {
            val modifiers = cls.modifiers
            if (cls.isMemberClass && !Modifier.isStatic(modifiers)) {
                throw JsonParsingException("Unsupported class: ${cls.name}. Inner non-static classes are not supported.")
            }
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)) {
                throw JsonParsingException("Unsupported class: ${cls.name}. Interfaces and abstract classes are not supported.")
            }
            val constructors = cls.declaredConstructors
            if (constructors.size > 1) {
                throw JsonParsingException("Class: ${cls.name} has more than one constructor.")
            }
            if (constructors.isEmpty()) {
                throw JsonParsingException("Class: ${cls.name} doesn't have any constructor.")
            }
        }

        private fun resolveKotlinParamNames(targetClass: Class<*>): List<String>? {
            try {
                val kClass = targetClass.kotlin
                if (kClass.isData) {
                    return kClass.primaryConstructor?.parameters?.map { it.name ?: return null }
                }
            } catch (_: Exception) {
                // kotlin-reflect not available or not a Kotlin class
            }
            return null
        }

        private fun resolveFieldName(parameter: Parameter, targetClass: Class<*>, kotlinParamName: String?): String {
            val annotation = parameter.getAnnotation(JsonFieldName::class.java)
            if (annotation != null) {
                return annotation.value
            }
            if (kotlinParamName != null) {
                return kotlinParamName
            }
            throw JsonParsingException(
                "Some of ${targetClass.name}'s constructor arguments are not annotated with @JsonFieldName."
            )
        }
    }
}
