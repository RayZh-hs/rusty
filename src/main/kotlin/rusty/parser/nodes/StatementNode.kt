package rusty.parser.nodes

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.putils.Context
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
                    var parsed: StatementNode? = null
                    if (ItemNode.peek(ctx)) return ItemStatementNode.parse(ctx)
                    parsed = ctx.tryParse(ExpressionStatementNode.name) {
                        ExpressionStatementNode.parse(ctx)
                    }
                    if (parsed != null)
                        return parsed

                    throw CompileError("Statement parsing failed").with(ctx)
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
        val typeNode: TypeNode,
        val expressionNode: ExpressionNode,
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
    // expect :Type
    putilsExpectToken(ctx, Token.O_COLUMN)
    val typeNode = TypeNode.parse(ctx)
    putilsExpectToken(ctx, Token.O_EQ)
    val expressionNode = ExpressionNode.parse(ctx)
    putilsExpectToken(ctx, Token.O_SEMICOLON)
    return StatementNode.LetStatementNode(patternNode, typeNode, expressionNode)
}

fun StatementNode.ItemStatementNode.Companion.parse(ctx: Context): StatementNode.ItemStatementNode {
    return StatementNode.ItemStatementNode(item = ItemNode.parse(ctx))
}

fun StatementNode.ExpressionStatementNode.Companion.parse(ctx: Context): StatementNode.ExpressionStatementNode {
    TODO()
}