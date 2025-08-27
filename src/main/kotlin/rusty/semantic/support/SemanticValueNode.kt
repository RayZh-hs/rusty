package rusty.semantic.support

import kotlin.reflect.KClass

sealed class SemanticValueNode {
    private annotation class Primitive
    private annotation class Virtual

    @Primitive data class I32Value(val value: Int) : SemanticValueNode()
    @Primitive data class U32Value(val value: UInt) : SemanticValueNode()
    @Primitive data class ISizeValue(val value: Int) : SemanticValueNode()
    @Primitive data class USizeValue(val value: UInt) : SemanticValueNode()
    @Primitive @Virtual class AnyIntValue(val value: Int) : SemanticValueNode() // can hold any integer type
    @Primitive @Virtual class AnySignedIntValue(val value: Int) : SemanticValueNode() // can hold any integer type
    @Primitive data class CharValue(val value: Char) : SemanticValueNode()
    @Primitive data class StringValue(val value: String) : SemanticValueNode()
    @Primitive data class CStringValue(val value: String) : SemanticValueNode()
    @Primitive data class BoolValue(val value: Boolean) : SemanticValueNode()
    @Primitive data object UnitValue : SemanticValueNode()

    data class ArrayValue(val arrayType: SemanticTypeNode.ArrayType, val elements: List<SemanticValueNode>, val repeat: SemanticValueNode.USizeValue) : SemanticValueNode()
    data class StructValue(val type: SemanticTypeNode.StructType, val fields: Map<String, SemanticValueNode>) : SemanticValueNode()
    data class EnumValue(val type: SemanticTypeNode.EnumType, val field: String) : SemanticValueNode()

    data class ReferenceValue(val referenced: SemanticValueNode) : SemanticValueNode()
}

fun List<SemanticValueNode>.commonKClass(): KClass<out SemanticValueNode>? {
    if (isEmpty()) return null
    val firstClass = first()::class
    return if (all { it::class == firstClass }) firstClass else null
}