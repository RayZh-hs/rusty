package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.lexer.TokenType
import rusty.lexer.getType
import rusty.parser.nodes.ExpressionNode
import rusty.parser.putils.Context
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode
import rusty.parser.nodes.utils.literalFromChar
import rusty.parser.nodes.utils.literalFromInteger
import rusty.parser.nodes.utils.literalFromString

fun WithoutBlockExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode {
    ctx.callMe("WithoutBlockExpression") {
        return parsePrecedence(ctx, 0)
    }
}

fun WithoutBlockExpressionNode.LiteralExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode {
    // consume the literal token
    val tokenBearer = ctx.stream.read()
    assert(tokenBearer.token.getType() == TokenType.LITERAL) {
        "Expected a literal token, but found ${tokenBearer.token.getType()}"
    }
    return when (tokenBearer.token) {
        Token.L_INTEGER -> {
            literalFromInteger(tokenBearer)
        }
        Token.L_STRING, Token.L_RAW_STRING,
        Token.L_C_STRING, Token.L_RAW_C_STRING -> {
            literalFromString(tokenBearer)
        }
        Token.L_CHAR -> {
            literalFromChar(tokenBearer)
        }
        else -> {
            throw CompileError("Unexpected literal token: ${tokenBearer.token}").with(ctx)
        }
    }
}

private fun parsePrecedence(ctx: Context, precedence: Int): WithoutBlockExpressionNode {
    TODO()
}