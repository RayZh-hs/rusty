package rusty.parser.nodes

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.utils.Peekable
import rusty.parser.putils.Context
import rusty.parser.putils.putilsExpectToken

// Since we don't need to implement OuterAttribute or MacroItem, Item directly corresponds to VisItem in our AST
@Peekable
sealed class ItemNode {
    companion object {
        fun peek(ctx: Context): Boolean {
            return when (ctx.peekToken()) {
                Token.K_FN, Token.K_STRUCT -> true
                else -> false
            }
        }

        fun parse(ctx: Context): ItemNode {
            if (FunctionItemNode.peek(ctx)) return FunctionItemNode.parse(ctx)
//            if (StructItemNode.peek(ctx)) return StructItemNode.parse(ctx)
            if (ctx.stream.atEnd())
                throw AssertionError("Item node parsing called upon null stream")
            else
                throw CompileError("Unknown token bearer ${ctx.stream.peekOrNull()} for ItemNode").with(ctx)
        }
    }

    @Peekable
    data class FunctionItemNode(
        val identifier: String,
        val genericParamsNode: ParamsNode.GenericParamsNode?,
        val functionParamsNode: ParamsNode.FunctionParamsNode?,
        val returnTypeNode: TypeNode?,
        val withBlockExpressionNode: ExpressionNode?
    ) : ItemNode() {
        companion object
    }
}

val ItemNode.FunctionItemNode.Companion.name get() = "FunctionItem"

fun ItemNode.FunctionItemNode.Companion.peek(ctx: Context): Boolean {
    return ctx.stream.peekOrNull()?.token == Token.K_FN
}

fun ItemNode.FunctionItemNode.Companion.parse(ctx: Context): ItemNode.FunctionItemNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.K_FN)
        val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
        val genericParamsNode = if (ctx.peekToken() == Token.O_LANG) ParamsNode.GenericParamsNode.parse(ctx) else null
        putilsExpectToken(ctx, Token.O_LPAREN)
        val functionParamsNode = if (ctx.peekToken() != Token.O_RPAREN) ParamsNode.FunctionParamsNode.parse(ctx) else null
        putilsExpectToken(ctx, Token.O_RPAREN)
        val returnTypeNode = when (ctx.peekToken()) {
            Token.O_ARROW -> {
                ctx.stream.consume(1)
                TypeNode.parse(ctx)
            }
            else -> null
        }
        // ignore WHERE clause
        val withBlockExpressionNode = when (ctx.stream.peekOrNull()?.token) {
            Token.O_LCURL -> ExpressionNode.WithBlockExpressionNode.parse(ctx)
            Token.O_SEMICOLON -> {
                ctx.stream.consume(1)
                null
            }
            else -> throw CompileError("Malformed function body at line ${ctx.stream.peekOrNull()?.lineNumber}").with(
                ctx
            )
        }
        return ItemNode.FunctionItemNode(
            identifier = identifier,
            genericParamsNode = genericParamsNode,
            functionParamsNode = functionParamsNode,
            returnTypeNode = returnTypeNode,
            withBlockExpressionNode = withBlockExpressionNode
        )
    }
}

//fun ItemNode.StructItemNode.Companion.peek(ctx: Context): Boolean {
//    return ctx.stream.peekOrNull()?.token == Token.K_STRUCT
//}

//fun ItemNode.StructItemNode.Companion.parse(ctx: Context): ItemNode.FunctionItemNode {
//}