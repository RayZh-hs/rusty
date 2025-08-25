package rusty.semantic.support

import rusty.core.utils.Slot

sealed class SemanticTypeNode {
    private annotation class Primitive

    data object UnknownType : SemanticTypeNode()

    @Primitive data object I32Type : SemanticTypeNode()
    @Primitive data object U32Type : SemanticTypeNode()
    @Primitive data object ISizeType : SemanticTypeNode()
    @Primitive data object USizeType : SemanticTypeNode()
    @Primitive data object CharType : SemanticTypeNode()
    @Primitive data object StringType : SemanticTypeNode()
    @Primitive data object BoolType : SemanticTypeNode()
    @Primitive data object UnitType : SemanticTypeNode()

    data class ArrayType(val elementType: Slot<SemanticTypeNode>) : SemanticTypeNode()
    data class SliceType(val elementType: Slot<SemanticTypeNode>) : SemanticTypeNode()

    // Tuples are removed; 0-tuples are converted into TypeUnit
    data class StructType(val identifier: String, val fields: Slot<Map<String, SemanticTypeNode>>) : SemanticTypeNode()
    data class EnumType(val identifier: String, val fields: Slot<List<String>>) : SemanticTypeNode()

    data class ReferenceType(val type: Slot<SemanticTypeNode>, val isMutable: Slot<Boolean>) : SemanticTypeNode()
}