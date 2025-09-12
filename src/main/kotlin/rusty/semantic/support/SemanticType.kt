package rusty.semantic.support

import rusty.core.utils.Slot
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.support.SelfParamNode

data class SemanticSelfNode(
    val isMut: Boolean,
    val isRef: Boolean,
    val type: Slot<SemanticType> = Slot(),
    val symbol: Slot<SemanticSymbol> = Slot(),
) {
    companion object {
        fun from(node: SelfParamNode): SemanticSelfNode {
            return SemanticSelfNode(
                isMut = node.isMutable,
                isRef = node.isReference,
            )
        }
    }

    fun fillWithSymbol(symbol: SemanticSymbol) {
        if (!this.type.isReady())
            when (symbol) {
                is SemanticSymbol.Struct -> this.type.set(symbol.definesType)
                is SemanticSymbol.Enum -> this.type.set(symbol.definesType)
                is SemanticSymbol.Trait -> this.type.set(symbol.definesType)
                else -> throw IllegalArgumentException("Self parameter must be of struct or enum type")
            }
        if (!this.symbol.isReady())
            this.symbol.set(symbol)
    }
}

data class SemanticFunctionParamNode(
    val pattern: PatternNode,
    val type: Slot<SemanticType> = Slot(),
)

sealed class SemanticType {
    sealed class Primitive : SemanticType()
    companion object {
        val RefStrType = ReferenceType(Slot(StrType), Slot(false))
        val RefCStrType = ReferenceType(Slot(CStrType), Slot(false))
        val RefMutStrType = ReferenceType(Slot(StrType), Slot(true))
        val RefMutCStrType = ReferenceType(Slot(CStrType), Slot(true))

        val StringStructType = StructType(
            identifier = "String",
            fields = mapOf()
        )
    }

    data object I32Type : Primitive()
    data object U32Type : Primitive()
    data object ISizeType : Primitive()
    data object USizeType : Primitive()
    data object AnyIntType : Primitive()
    data object AnySignedIntType : Primitive()
    data object CharType : Primitive()
    data object StrType : Primitive()
    data object CStrType : Primitive()
    data object BoolType : Primitive()
    data object UnitType : Primitive()
    data object NeverType : SemanticType() // Used in type inference

    data object WildcardType : SemanticType() // Used in type inference

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

    data class TraitType(
        val identifier: String
    ) : SemanticType()

    data class ReferenceType(val type: Slot<SemanticType>, val isMutable: Slot<Boolean>) : SemanticType()
    data class FunctionHeader(val identifier: String, val selfParamType: SemanticType?, val paramTypes: List<SemanticType>, val returnType: SemanticType) : SemanticType()
}