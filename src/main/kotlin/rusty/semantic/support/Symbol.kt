package rusty.semantic.support

import rusty.core.utils.Slot
import rusty.parser.nodes.ASTNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.support.FunctionParamNode
import rusty.parser.nodes.support.SelfParamNode

data class SemanticSelfNode(
    val isMut: Boolean,
    val isRef: Boolean,
    val type: Slot<SemanticTypeNode> = Slot(),
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
    val type: Slot<SemanticTypeNode> = Slot(),
)

sealed interface Symbol {
    val identifier: String
    val definedAt: ASTNode? // a null definition means the symbol is defined in the prelude

    data class Variable(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val mutable: Slot<Boolean> = Slot(),
        val type: Slot<SemanticTypeNode> = Slot(),

    ) : Symbol

    data class Function(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val selfParam: Slot<SemanticSelfNode?> = Slot(),
        val funcParams: Slot<List<SemanticFunctionParamNode>> = Slot(),
        val returnType: Slot<SemanticTypeNode> = Slot(),

        ) : Symbol

    data class Struct(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val variables: Slot<Map<String, SemanticTypeNode>> = Slot(),
        // a struct can be implemented multiple times, so we store its functions and constants in a mutable map instead of a slot
        val functions: MutableMap<String, Function> = mutableMapOf(),
        val constants: MutableMap<String, Const> = mutableMapOf(),

        ) : Symbol

    data class Enum(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val elements: Slot<List<String>> = Slot(),

    ) : Symbol

    data class Const(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val type: Slot<SemanticTypeNode> = Slot(),
        val value: Slot<SemanticValueNode> = Slot(),
    ) : Symbol

    data class Trait(
        override val identifier: String,
        override val definedAt: ASTNode?,

        val functions: Slot<Map<String, Function>> = Slot(),
        val constants: Slot<Map<String, Const>> = Slot(),

    ) : Symbol
}