package rusty.semantic.support

import rusty.core.utils.Slot
import rusty.parser.nodes.ASTNode

sealed interface SemanticSymbol {
    val identifier: String
    val definedAt: ASTNode? // a null definition means the symbol is defined in the prelude

    data class Variable(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val mutable: Slot<Boolean> = Slot(),
        val type: Slot<SemanticType> = Slot(),

        ) : SemanticSymbol

    data class Function(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val selfParam: Slot<SemanticSelfNode?> = Slot(),
        val funcParams: Slot<List<SemanticFunctionParamNode>> = Slot(),
        val returnType: Slot<SemanticType> = Slot(),

        ) : SemanticSymbol

    data class Struct(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val definesType: SemanticType.StructType,
        val functions: MutableMap<String, Function> = mutableMapOf(),
        val constants: MutableMap<String, Const> = mutableMapOf(),

        ) : SemanticSymbol {
        val fields: Map<String, Slot<SemanticType>>
            get() = definesType.fields
    }

    data class Enum(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val definesType: SemanticType.EnumType,
        val functions: MutableMap<String, Function> = mutableMapOf(),
        val constants: MutableMap<String, Const> = mutableMapOf(),

        ) : SemanticSymbol {
        val fields: List<String>?
            get() = definesType.fields.getOrNull()
    }

    data class Const(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val type: Slot<SemanticType> = Slot(),
        val value: Slot<SemanticValue> = Slot(),

    ) : SemanticSymbol

    data class Trait(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val functions: Map<String, Function> = mapOf(),
        val constants: Map<String, Const> = mapOf(),

    ) : SemanticSymbol
}