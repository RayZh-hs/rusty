package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.lexer.TokenType
import rusty.lexer.getType
import rusty.parser.putils.Context
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode
import rusty.parser.nodes.utils.literalFromChar
import rusty.parser.nodes.utils.literalFromInteger
import rusty.parser.nodes.utils.literalFromString

fun WithoutBlockExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode {
    TODO()
}

fun WithoutBlockExpressionNode.LiteralExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode {
    // consume the literal token
    val tokenBearer = ctx.stream.read()
    assert(tokenBearer.token.getType() == TokenType.LITERAL) {
        "Expected a literal token, but found ${tokenBearer.token.getType()}"
    }
    when (tokenBearer.token) {
        Token.L_INTEGER -> {
            return literalFromInteger(tokenBearer)
        }

        Token.L_STRING, Token.L_RAW_STRING,
        Token.L_C_STRING, Token.L_RAW_C_STRING -> {
            return literalFromString(tokenBearer)
        }

        Token.L_CHAR -> {
            return literalFromChar(tokenBearer)
        }

        else -> {
            throw CompileError("Unexpected literal token: ${tokenBearer.token}").with(ctx)
        }
    }
}