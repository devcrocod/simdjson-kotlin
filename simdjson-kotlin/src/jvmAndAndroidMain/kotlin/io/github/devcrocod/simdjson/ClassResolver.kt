package io.github.devcrocod.simdjson

import java.lang.reflect.Type

internal class ClassResolver {

    private val classCache = HashMap<Type, ResolvedClass>()

    fun resolveClass(type: Type): ResolvedClass {
        classCache[type]?.let { return it }
        val resolved = ResolvedClass(type, this)
        classCache[type] = resolved
        return resolved
    }

    fun reset() {
        classCache.clear()
    }
}
