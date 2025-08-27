package rusty.semantic.support

sealed class SemanticValueNode {
    private annotation class Primitive

    @Primitive data class I32Value(val value: Int) : SemanticValueNode()
    @Primitive data class U32Value(val value: UInt) : SemanticValueNode()
    @Primitive data class ISizeValue(val value: Int) : SemanticValueNode()
    @Primitive data class USizeValue(val value: UInt) : SemanticValueNode()
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