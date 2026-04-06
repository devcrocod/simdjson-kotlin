package io.github.devcrocod.simdjson.schemas

import io.github.devcrocod.simdjson.annotations.JsonFieldName

// --- Data classes (like Java records: implicit param names) ---

// Boxed/nullable single-field
data class DataClassWithBooleanField(val field: Boolean?)
data class DataClassWithByteField(val field: Byte?)
data class DataClassWithShortField(val field: Short?)
data class DataClassWithIntField(val field: Int?)
data class DataClassWithLongField(val field: Long?)
data class DataClassWithFloatField(val field: Float?)
data class DataClassWithDoubleField(val field: Double?)
data class DataClassWithCharField(val field: Char?)
data class DataClassWithStringField(val field: String?)

// Primitive/non-nullable single-field
data class DataClassWithPrimitiveBooleanField(val field: Boolean)
data class DataClassWithPrimitiveByteField(val field: Byte)
data class DataClassWithPrimitiveShortField(val field: Short)
data class DataClassWithPrimitiveIntField(val field: Int)
data class DataClassWithPrimitiveLongField(val field: Long)
data class DataClassWithPrimitiveFloatField(val field: Float)
data class DataClassWithPrimitiveDoubleField(val field: Double)
data class DataClassWithPrimitiveCharField(val field: Char)

// Array fields (boxed)
data class DataClassWithBooleanArrayField(val field: Array<Boolean?>?)
data class DataClassWithByteArrayField(val field: Array<Byte?>?)
data class DataClassWithShortArrayField(val field: Array<Short?>?)
data class DataClassWithIntArrayField(val field: Array<Int?>?)
data class DataClassWithLongArrayField(val field: Array<Long?>?)
data class DataClassWithFloatArrayField(val field: Array<Float?>?)
data class DataClassWithDoubleArrayField(val field: Array<Double?>?)
data class DataClassWithCharArrayField(val field: Array<Char?>?)
data class DataClassWithStringArrayField(val field: Array<String?>?)

// Array fields (primitive)
data class DataClassWithPrimitiveBooleanArrayField(val field: BooleanArray?)
data class DataClassWithPrimitiveByteArrayField(val field: ByteArray?)
data class DataClassWithPrimitiveShortArrayField(val field: ShortArray?)
data class DataClassWithPrimitiveIntArrayField(val field: IntArray?)
data class DataClassWithPrimitiveLongArrayField(val field: LongArray?)
data class DataClassWithPrimitiveFloatArrayField(val field: FloatArray?)
data class DataClassWithPrimitiveDoubleArrayField(val field: DoubleArray?)
data class DataClassWithPrimitiveCharArrayField(val field: CharArray?)

// List fields
data class DataClassWithBooleanListField(val field: List<Boolean>?)
data class DataClassWithByteListField(val field: List<Byte>?)
data class DataClassWithShortListField(val field: List<Short>?)
data class DataClassWithIntListField(val field: List<Int>?)
data class DataClassWithLongListField(val field: List<Long>?)
data class DataClassWithFloatListField(val field: List<Float>?)
data class DataClassWithDoubleListField(val field: List<Double>?)
data class DataClassWithCharListField(val field: List<Char>?)
data class DataClassWithStringListField(val field: List<String>?)

// Multi-field and nested
data class DataClassWithMultipleFields(val name: String?, val age: Int?, val active: Boolean?)
data class NestedDataClass(val nestedField: DataClassWithStringField?)
data class DataClassWithObjectArrayField(val field: Array<DataClassWithIntField?>?)
data class DataClassWithObjectListField(val field: List<DataClassWithIntField>?)
data class DataClassWith2DIntArray(val field: Array<IntArray?>?)

// --- Non-data classes (require @JsonFieldName) ---

class AnnotatedClassWithStringField(@JsonFieldName("field") val field: String?)
class AnnotatedClassWithIntField(@JsonFieldName("field") val field: Int?)
class AnnotatedClassWithPrimitiveIntField(@JsonFieldName("field") val field: Int)
class AnnotatedClassWithPrimitiveBooleanField(@JsonFieldName("field") val field: Boolean)
class AnnotatedClassWithPrimitiveByteField(@JsonFieldName("field") val field: Byte)
class AnnotatedClassWithPrimitiveShortField(@JsonFieldName("field") val field: Short)
class AnnotatedClassWithPrimitiveLongField(@JsonFieldName("field") val field: Long)
class AnnotatedClassWithPrimitiveFloatField(@JsonFieldName("field") val field: Float)
class AnnotatedClassWithPrimitiveDoubleField(@JsonFieldName("field") val field: Double)
class AnnotatedClassWithPrimitiveCharField(@JsonFieldName("field") val field: Char)

// Explicit field name mapping
data class DataClassWithExplicitFieldNames(
    @JsonFieldName("json_name") val kotlinName: String?,
    @JsonFieldName("json_age") val kotlinAge: Int?
)

// Unicode field name
data class DataClassWithUnicodeFieldName(@JsonFieldName("\u0105\u0107\u015B\u0144\u017A\u017C") val field: String?)
