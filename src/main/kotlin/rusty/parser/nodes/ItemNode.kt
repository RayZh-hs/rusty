package rusty.parser.nodes

import rusty.core.CompileError
import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.impl.peek
import rusty.parser.nodes.support.AssociatedItemsNode
import rusty.parser.nodes.support.EnumVariantNode
import rusty.parser.nodes.support.StructExprFieldNode
import rusty.parser.nodes.support.StructFieldNode
import rusty.parser.nodes.utils.Parsable
import rusty.parser.nodes.utils.Peekable
import rusty.parser.putils.Context

// Since we don't need to implement OuterAttribute or MacroItem, Item directly corresponds to VisItem in our AST
@Peekable @Parsable
sealed class ItemNode(pointer: CompilerPointer): ASTNode(pointer) {
    companion object {
        fun peek(ctx: Context): Boolean {
            return when (ctx.peekToken()) {
                Token.K_FN, Token.K_STRUCT, Token.K_IMPL, Token.K_CONST, Token.K_ENUM, Token.K_TRAIT -> true
                else -> false
            }
        }

        fun parse(ctx: Context): ItemNode {
            if (FunctionItemNode.peek(ctx)) return FunctionItemNode.parse(ctx)
            if (StructItemNode.peek(ctx)) return StructItemNode.parse(ctx)
            if (EnumItemNode.peek(ctx)) return EnumItemNode.parse(ctx)
            if (ConstItemNode.peek(ctx)) return ConstItemNode.parse(ctx)
            if (TraitItemNode.peek(ctx)) return TraitItemNode.parse(ctx)
            if (ImplItemNode.peek(ctx)) return ImplItemNode.parse(ctx)

            if (ctx.stream.atEnd())
                throw AssertionError("Item node parsing called upon null stream")
            else
                throw CompileError("Unknown token bearer ${ctx.stream.peekOrNull()} for ItemNode").with(ctx).at(ctx.peekPointer())
        }
    }

    @Peekable @Parsable
    data class FunctionItemNode(
        val identifier: String,
        val genericParamsNode: ParamsNode.GenericParamsNode?,
        val functionParamsNode: ParamsNode.FunctionParamsNode,
        val returnTypeNode: TypeNode?,
        val withBlockExpressionNode: ExpressionNode?,
        override val pointer: CompilerPointer
    ) : ItemNode(pointer) {
        companion object
    }

    @Peekable @Parsable
    data class StructItemNode(
        val identifier: String,
        val fields: List<StructFieldNode>,
        val isDeclaration: Boolean,
        override val pointer: CompilerPointer
    ) : ItemNode(pointer) {
        companion object
    }

    @Peekable @Parsable
    data class EnumItemNode(
        val identifier: String,
        val variants: List<EnumVariantNode>,
        override val pointer: CompilerPointer
    ) : ItemNode(pointer) {
        companion object
    }

    @Peekable @Parsable
    data class ConstItemNode(
        val identifier: String,
        val typeNode: TypeNode,
        val expressionNode: ExpressionNode?,
        override val pointer: CompilerPointer
    ) : ItemNode(pointer) {
        companion object
    }

    @Peekable @Parsable
    data class TraitItemNode(
        val identifier: String,
        val associatedItems: AssociatedItemsNode,
        override val pointer: CompilerPointer
    ) : ItemNode(pointer) {
        companion object
    }

    @Peekable @Parsable
    sealed class ImplItemNode(pointer: CompilerPointer) : ItemNode(pointer) {
        companion object;

        data class InherentImplItemNode(
            val typeNode: TypeNode,
            val associatedItems: AssociatedItemsNode,
            override val pointer: CompilerPointer
        ) : ImplItemNode(pointer)

        data class TraitImplItemNode(
            val identifier: String,
            val typeNode: TypeNode,
            val associatedItems: AssociatedItemsNode,
            override val pointer: CompilerPointer
        ) : ImplItemNode(pointer)
    }
}
