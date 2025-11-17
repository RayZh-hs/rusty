package rusty.parser.nodes

import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.impl.peek
import rusty.parser.nodes.utils.afterWhich
import rusty.parser.putils.ParsingContext
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken
import rusty.parser.nodes.utils.Parsable
import rusty.parser.nodes.utils.Peekable

@Parsable
sealed class StatementNode(pointer: CompilerPointer): ASTNode(pointer) {
    companion object {
        val name get() = "Statement"

        fun parse(ctx: ParsingContext): StatementNode {
            when (ctx.peekToken()) {
                null -> throw AssertionError("Statement node parsing called upon null stream")
                Token.O_SEMICOLON -> return NullStatementNode(ctx.peekPointer()).afterWhich {
                    ctx.stream.consume(1) // consume the semicolon
                }
                Token.K_LET -> return LetStatementNode.parse(ctx)
                else -> {
                    if (ItemNode.peek(ctx)) return ItemStatementNode.parse(ctx)
                    return ExpressionStatementNode.parse(ctx)
                }
            }
        }
    }

    data class NullStatementNode(override val pointer: CompilerPointer) : StatementNode(pointer)

    @Parsable
    data class ItemStatementNode(val item: ItemNode, override val pointer: CompilerPointer) : StatementNode(pointer) {
        companion object {
            val name get() = "ItemStatement"
        }
    }

    @Peekable @Parsable
    data class LetStatementNode(
        val patternNode: PatternNode,
        val typeNode: TypeNode?,
        val expressionNode: ExpressionNode?,
        override val pointer: CompilerPointer
    ) : StatementNode(pointer) {
        companion object {
            val name get() = "LetStatement"
        }
    }

    @Parsable
    data class ExpressionStatementNode(val expression: ExpressionNode, override val pointer: CompilerPointer) : StatementNode(pointer) {
        companion object {
            val name get() = "ExpressionStatement"
        }
    }
}

fun StatementNode.LetStatementNode.Companion.peek(ctx: ParsingContext): Boolean {
    return ctx.peekToken() == Token.K_LET
}

fun StatementNode.LetStatementNode.Companion.parse(ctx: ParsingContext): StatementNode {
    ctx.callMe(name) {
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
        return StatementNode.LetStatementNode(patternNode, typeNode, expressionNode, ctx.topPointer())
    }
}

fun StatementNode.ItemStatementNode.Companion.parse(ctx: ParsingContext): StatementNode.ItemStatementNode {
    ctx.callMe(name) {
        return StatementNode.ItemStatementNode(item = ItemNode.parse(ctx), pointer = ctx.topPointer())
    }
}

fun StatementNode.ExpressionStatementNode.Companion.parse(ctx: ParsingContext): StatementNode.ExpressionStatementNode {
    ctx.callMe(name) {
        return StatementNode.ExpressionStatementNode(
            expression = if (ExpressionNode.WithBlockExpressionNode.peek(ctx)) {
                ExpressionNode.WithBlockExpressionNode.parse(ctx).afterWhich {
                    putilsConsumeIfExistsToken(ctx, Token.O_SEMICOLON)
                }
            } else {
                ExpressionNode.WithoutBlockExpressionNode.parse(ctx).afterWhich {
                    putilsExpectToken(ctx, Token.O_SEMICOLON)
                }
            },
            pointer = ctx.topPointer()
        )
    }
}