package rusty.semantic.support

import rusty.core.utils.Slot
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.support.SelfParamNode

data class SemanticSelfNode(
    val isMut: Boolean,
    val isRef: Boolean,
    val type: Slot<SemanticType> = Slot(),
) {
    companion object {
        fun from(node: SelfParamNode): SemanticSelfNode {
            return SemanticSelfNode(
                isMut = node.isMutable,
                isRef = node.isReference,
            )
        }
    }
}

data class SemanticFunctionParamNode(
    val pattern: PatternNode,
    val type: Slot<SemanticType> = Slot(),
)

sealed class SemanticType {
    private annotation class Primitive
    private annotation class Internal

    @Primitive data object I32Type : SemanticType()
    @Primitive data object U32Type : SemanticType()
    @Primitive data object ISizeType : SemanticType()
    @Primitive data object USizeType : SemanticType()
    @Primitive data object AnyIntType : SemanticType()
    @Primitive data object AnySignedIntType : SemanticType()
    @Primitive data object CharType : SemanticType()
    @Primitive data object StringType : SemanticType()
    @Primitive data object CStringType : SemanticType()
    @Primitive data object BoolType : SemanticType()
    @Primitive data object UnitType : SemanticType()

    data class ArrayType(val elementType: Slot<SemanticType>, val length: Slot<SemanticValue.USizeValue>) : SemanticType()

    // Tuples are removed; 0-tuples are converted into TypeUnit
    data class StructType(
        val identifier: String,
        val fields: Map<String, Slot<SemanticType>>,
        ) : SemanticType()

    data class EnumType(
        val identifier: String,
        val fields: Slot<List<String>> = Slot(),
    ) : SemanticType()

    data class ReferenceType(val type: Slot<SemanticType>, val isMutable: Slot<Boolean>) : SemanticType()
}