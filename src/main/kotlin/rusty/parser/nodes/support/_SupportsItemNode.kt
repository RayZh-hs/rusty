package rusty.parser.nodes.support

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.impl.parse
import rusty.parser.putils.Context
import rusty.parser.putils.putilsExpectToken

data class StructFieldNode(val identifier: String, val typeNode: TypeNode) {
    companion object {
        fun parse(ctx: Context): StructFieldNode {
            val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
            putilsExpectToken(ctx, Token.O_COLUMN)
            val typeNode = TypeNode.parse(ctx)
            return StructFieldNode(identifier, typeNode)
        }
    }
}

data class EnumVariantNode(val identifier: String) {
    companion object {
        fun parse(ctx: Context): EnumVariantNode {
            val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
            return EnumVariantNode(identifier)
        }
    }
}

data class AssociatedItemsNode(
    val constItems: List<ItemNode.ConstItemNode>,
    val functionItems: List<ItemNode.FunctionItemNode>,
) {
    companion object {
        fun parse(ctx: Context): AssociatedItemsNode {
            val constItems = mutableListOf<ItemNode.ConstItemNode>()
            val functionItems = mutableListOf<ItemNode.FunctionItemNode>()
            putilsExpectToken(ctx, Token.O_LCURL)
            while (ctx.peekToken() != Token.O_RCURL) {
                when (ctx.peekToken()) {
                    Token.K_CONST -> constItems.add(ItemNode.ConstItemNode.parse(ctx))
                    Token.K_FN -> functionItems.add(ItemNode.FunctionItemNode.parse(ctx))
                    else -> throw CompileError("Associated items: Unexpected token ${ctx.peekToken()} at ${ctx.stream.peekOrNull()?.lineNumber}:${ctx.stream.peekOrNull()?.columnNumber}").with(ctx).at(ctx.peekPointer())
                }
            }
            ctx.stream.consume(1)
            return AssociatedItemsNode(constItems, functionItems)
        }
    }
}