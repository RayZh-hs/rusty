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

    data object I32Type : Primitive() {
        override fun toString(): String = "i32"
    }
    data object U32Type : Primitive() {
        override fun toString(): String = "u32"
    }
    data object ISizeType : Primitive() {
        override fun toString(): String = "isize"
    }
    data object USizeType : Primitive() {
        override fun toString(): String = "usize"
    }
    data object AnyIntType : Primitive() {
        override fun toString(): String = "<int>"
    }
    data object AnySignedIntType : Primitive() {
        override fun toString(): String = "<signed_int>"
    }
    data object CharType : Primitive() {
        override fun toString(): String = "char"
    }
    data object StrType : Primitive() {
        override fun toString(): String = "str"
    }
    data object CStrType : Primitive() {
        override fun toString(): String = "cstr"
    }
    data object BoolType : Primitive() {
        override fun toString(): String = "bool"
    }
    data object UnitType : Primitive() {
        override fun toString(): String = "()"
    }
    data object NeverType : SemanticType() {
        // Used in type inference
        override fun toString(): String = "!"
    }

    data object WildcardType : SemanticType() {
        // Used in type inference
        override fun toString(): String = "_"
    }

    data class ArrayType(val elementType: Slot<SemanticType>, val length: Slot<SemanticValue.USizeValue>) : SemanticType() {
        override fun toString(): String = "[${elementType.getOrNull()}; ${length.getOrNull()?.value}]"
    }

    // Tuples are removed; 0-tuples are converted into TypeUnit
    data class StructType(
        val identifier: String,
        val fields: Map<String, Slot<SemanticType>>,
        ) : SemanticType() {
        override fun toString(): String = "(struct)$identifier"
    }

    data class EnumType(
        val identifier: String,
        val fields: Slot<List<String>> = Slot(),
    ) : SemanticType() {
        override fun toString(): String = "(enum)$identifier"
    }

    data class TraitType(
        val identifier: String,
        val scope: Scope,
    ) : SemanticType() {
        override fun toString(): String = "(trait)$identifier"
    }

    data class ReferenceType(val type: Slot<SemanticType>, val isMutable: Slot<Boolean>) : SemanticType() {
        override fun toString(): String {
            return when (isMutable.getOrNull()) {
                true -> "&mut${type.getOrNull()}"
                false -> "&${type.getOrNull()}"
                null -> "&?${type.getOrNull()}"
            }
        }
    }
    data class FunctionHeader(val identifier: String, val selfParamType: SemanticType?, val paramTypes: List<SemanticType>, val returnType: SemanticType) : SemanticType() {
        override fun toString(): String {
            val params = buildString {
                if (selfParamType != null) {
                    append(selfParamType.toString())
                    if (paramTypes.isNotEmpty()) append(", ")
                }
                append(paramTypes.joinToString(", ") { it.toString() })
            }
            return "(fn)$identifier($params) -> $returnType"
        }
    }
}