package rusty.semantic.support

import kotlin.reflect.KClass

sealed class SemanticValue {
    private annotation class Primitive
    private annotation class Virtual

    @Primitive data class I32Value(val value: Int) : SemanticValue()
    @Primitive data class U32Value(val value: UInt) : SemanticValue()
    @Primitive data class ISizeValue(val value: Int) : SemanticValue()
    @Primitive data class USizeValue(val value: UInt) : SemanticValue()
    @Primitive @Virtual class AnyIntValue(val value: Int) : SemanticValue() // can hold any integer type
    @Primitive @Virtual class AnySignedIntValue(val value: Int) : SemanticValue() // can hold any integer type
    @Primitive data class CharValue(val value: Char) : SemanticValue()
    @Primitive data class StringValue(val value: String) : SemanticValue()
    @Primitive data class CStringValue(val value: String) : SemanticValue()
    @Primitive data class BoolValue(val value: Boolean) : SemanticValue()
    @Primitive data object UnitValue : SemanticValue()

    data class ArrayValue(val arrayType: SemanticType.ArrayType, val elements: List<SemanticValue>, val repeat: SemanticValue.USizeValue) : SemanticValue()
    data class StructValue(val type: SemanticType.StructType, val fields: Map<String, SemanticValue>) : SemanticValue()
    data class EnumValue(val type: SemanticType.EnumType, val field: String) : SemanticValue()

    data class ReferenceValue(val referenced: SemanticValue) : SemanticValue()
}

fun List<SemanticValue>.commonKClass(): KClass<out SemanticValue>? {
    if (isEmpty()) return null
    val firstClass = first()::class
    return if (all { it::class == firstClass }) firstClass else null
}