package rusty.parser.nodes

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.impl.peek
import rusty.parser.nodes.utils.afterWhich
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken

sealed class StatementNode {
    companion object {
        val name get() = "Statement"

        fun parse(ctx: Context): StatementNode {
            when (ctx.peekToken()) {
                null -> throw AssertionError("Statement node parsing called upon null stream")
                Token.O_SEMICOLON -> return NullStatementNode
                Token.K_LET -> return LetStatementNode.parse(ctx)
                else -> {
                    if (ItemNode.peek(ctx)) return ItemStatementNode.parse(ctx)
                    return ExpressionStatementNode.parse(ctx)
                }
            }
        }
    }

    data object NullStatementNode : StatementNode()
    data class ItemStatementNode(val item: ItemNode) : StatementNode() {
        companion object {
            val name get() = "ItemStatement"
        }
    }

    data class LetStatementNode(
        val patternNode: PatternNode,
        val typeNode: TypeNode?,
        val expressionNode: ExpressionNode?,
    ) : StatementNode() {
        companion object
    }

    data class ExpressionStatementNode(val expression: ExpressionNode) : StatementNode() {
        companion object {
            val name get() = "ExpressionStatement"
        }
    }
}

fun StatementNode.LetStatementNode.Companion.peek(ctx: Context): Boolean {
    return ctx.peekToken() == Token.K_LET
}

fun StatementNode.LetStatementNode.Companion.parse(ctx: Context): StatementNode {
    putilsExpectToken(ctx, Token.K_LET)
    val patternNode = PatternNode.parse(ctx)
    var typeNode: TypeNode? = null
    if (ctx.peekToken() == Token.O_COLUMN) {
        ctx.stream.consume(1)   // : type
        typeNode = TypeNode.parse(ctx)
    }
    var expressionNode: ExpressionNode? = null
    if (putilsConsumeIfExistsToken(ctx, Token.O_EQ)) {
        expressionNode = ExpressionNode.parse(ctx)
    }
    putilsExpectToken(ctx, Token.O_SEMICOLON)
    return StatementNode.LetStatementNode(patternNode, typeNode, expressionNode)
}

fun StatementNode.ItemStatementNode.Companion.parse(ctx: Context): StatementNode.ItemStatementNode {
    return StatementNode.ItemStatementNode(item = ItemNode.parse(ctx))
}

fun StatementNode.ExpressionStatementNode.Companion.parse(ctx: Context): StatementNode.ExpressionStatementNode {
    return StatementNode.ExpressionStatementNode(
        expression = if (ExpressionNode.WithBlockExpressionNode.peek(ctx)) {
            ExpressionNode.WithBlockExpressionNode.parse(ctx).afterWhich {
                putilsConsumeIfExistsToken(ctx, Token.O_SEMICOLON)
            }
        } else {
            ExpressionNode.WithoutBlockExpressionNode.parse(ctx).afterWhich {
                putilsExpectToken(ctx, Token.O_SEMICOLON)
            }
        }
    )
}