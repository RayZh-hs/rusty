package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.lexer.TokenType
import rusty.lexer.getType
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode
import rusty.parser.nodes.PathInExpressionNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.utils.literalFromBoolean
import rusty.parser.nodes.utils.literalFromChar
import rusty.parser.nodes.utils.literalFromInteger
import rusty.parser.nodes.utils.literalFromString
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken

val WithoutBlockExpressionNode.Companion.name get() = "WithoutBlockExpression"

fun WithoutBlockExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode {
    ctx.callMe(name) {
        return parsePrecedence(ctx, Precedence.NONE.value)
    }
}

private enum class Precedence(val value: Int) {
    NONE(0),
    // this is delegated to RightAssociativeOperator, and is handled differently
    ASSIGNMENT(10),  // = += -= etc.
    RANGE(20),       // fix: need not implement
    LOGICAL_OR(30),  // ||
    LOGICAL_AND(40), // &&
    COMPARISON(50),  // == != < > <= >=
    BITWISE_OR(60),  // |
    BITWISE_XOR(70), // ^
    BITWISE_AND(80), // &
    SHIFT(90),       // << >>
    TERM(100),       // + -
    FACTOR(110),     // * / %
    CAST(120),       // as
    PREFIX(130),     // - ! &
    POSTFIX(140)     // () [] . ::
}

private fun getTokenPrecedence(token: Token?): Precedence {
    return when (token) {
        Token.O_EQ, Token.O_PLUS_EQ, Token.O_MINUS_EQ, Token.O_STAR_EQ, Token.O_DIV_EQ,
        Token.O_PERCENT_EQ, Token.O_AND_EQ, Token.O_OR_EQ, Token.O_XOR_EQ, Token.O_SLFT_EQ, Token.O_SRIT_EQ -> Precedence.ASSIGNMENT

        Token.O_DOUBLE_OR -> Precedence.LOGICAL_OR
        Token.O_DOUBLE_AND -> Precedence.LOGICAL_AND
        Token.O_DOUBLE_EQ, Token.O_NEQ, Token.O_LANG, Token.O_RANG, Token.O_LEQ, Token.O_GEQ -> Precedence.COMPARISON
        Token.O_OR -> Precedence.BITWISE_OR
        Token.O_BIT_XOR -> Precedence.BITWISE_XOR
        Token.O_AND -> Precedence.BITWISE_AND
        Token.O_SLFT, Token.O_SRIT -> Precedence.SHIFT
        Token.O_PLUS, Token.O_MINUS -> Precedence.TERM
        Token.O_STAR, Token.O_DIV, Token.O_PERCENT -> Precedence.FACTOR
        Token.K_AS -> Precedence.CAST
        Token.O_LPAREN, Token.O_LSQUARE, Token.O_DOT, Token.O_DOUBLE_COLON -> Precedence.POSTFIX
        else -> Precedence.NONE
    }
}

private typealias NudParselet = (Context) -> WithoutBlockExpressionNode
private typealias LedParselet = (Context, WithoutBlockExpressionNode) -> WithoutBlockExpressionNode

// Map for NUD functions. Used for tokens that start an expression.
private val nudParselets: Map<Token, NudParselet> = mapOf(
    // Literals
    Token.L_INTEGER to ::parseLiteral,
    Token.L_STRING to ::parseLiteral,
    Token.L_RAW_STRING to ::parseLiteral,
    Token.L_C_STRING to ::parseLiteral,
    Token.L_RAW_C_STRING to ::parseLiteral,
    Token.L_CHAR to ::parseLiteral,
    Token.K_TRUE to ::parseLiteral,
    Token.K_FALSE to ::parseLiteral,
    Token.O_UNDERSCORE to ::parseUnderscore,

    // Identifier / Path
    Token.I_IDENTIFIER to ::parsePathExpression,
    Token.O_DOUBLE_COLON to ::parsePathExpression,

    // Prefix Operators
    Token.O_MINUS to ::parsePrefixOperator,
    Token.O_NOT to ::parsePrefixOperator,
    Token.O_AND to ::parsePrefixOperator, // For borrows like &foo

    // Grouped, Tuple, and Array Expressions
    Token.O_LPAREN to ::parseGroupedOrTupleExpression,
    Token.O_LSQUARE to ::parseArrayExpression,

    // Control Flow
    Token.K_RETURN to ::parseReturnExpression,
    Token.K_BREAK to ::parseBreakExpression,
    Token.K_CONTINUE to { WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode }
)

// Map for LED functions. Used for infix and postfix operators.
private val ledParselets: Map<Token, LedParselet> = mapOf(
    // Infix Operators
    Token.O_PLUS to ::parseLAInfixOperator, Token.O_MINUS to ::parseLAInfixOperator,
    Token.O_STAR to ::parseLAInfixOperator, Token.O_DIV to ::parseLAInfixOperator,
    Token.O_PERCENT to ::parseLAInfixOperator, Token.O_DOUBLE_AND to ::parseLAInfixOperator,
    Token.O_DOUBLE_OR to ::parseLAInfixOperator, Token.O_DOUBLE_EQ to ::parseLAInfixOperator,
    Token.O_NEQ to ::parseLAInfixOperator, Token.O_LANG to ::parseLAInfixOperator,
    Token.O_RANG to ::parseLAInfixOperator, Token.O_LEQ to ::parseLAInfixOperator,
    Token.O_GEQ to ::parseLAInfixOperator, Token.O_AND to ::parseLAInfixOperator,
    Token.O_OR to ::parseLAInfixOperator, Token.O_BIT_XOR to ::parseLAInfixOperator,
    Token.O_SLFT to ::parseLAInfixOperator, Token.O_SRIT to ::parseLAInfixOperator,

    // Assignment Operators
    Token.O_EQ to ::parseRAInfixOperator, Token.O_PLUS_EQ to ::parseRAInfixOperator,
    Token.O_MINUS_EQ to ::parseRAInfixOperator, Token.O_STAR_EQ to ::parseRAInfixOperator,
    Token.O_DIV_EQ to ::parseRAInfixOperator, Token.O_PERCENT_EQ to ::parseRAInfixOperator,
    Token.O_AND_EQ to ::parseRAInfixOperator, Token.O_OR_EQ to ::parseRAInfixOperator,
    Token.O_XOR_EQ to ::parseRAInfixOperator, Token.O_SLFT_EQ to ::parseRAInfixOperator,
    Token.O_SRIT_EQ to ::parseRAInfixOperator,

    // Postfix/Call Operators
    Token.O_LPAREN to ::parseCallExpression,
    Token.O_LSQUARE to ::parseIndexExpression,
    Token.O_DOT to ::parseFieldOrTupleIndexExpression
)

private fun parsePrecedence(ctx: Context, precedence: Int): WithoutBlockExpressionNode {
    val currentToken = ctx.stream.read()
    ctx.prattProcessingTokenBearer = currentToken // Store the token for literal conversion
    val nud = nudParselets[currentToken.token]
        ?: throw CompileError("Invalid start of expression: ${currentToken.token}").with(ctx)

    var left = nud(ctx)

    while (precedence < getTokenPrecedence(ctx.peekToken()).value) {
        val nextToken = ctx.peekToken()!!
        val led = ledParselets[nextToken] ?: break // Not an infix/postfix operator
        ctx.prattProcessingTokenBearer = ctx.stream.read()
        left = led(ctx, left)
    }
    return left
}

private fun parseLiteral(ctx: Context): WithoutBlockExpressionNode {
    val tokenBearer =
        ctx.prattProcessingTokenBearer ?: throw CompileError("Expected a literal token; found none").with(ctx)
    val literalToken = tokenBearer.token

    assert(literalToken.getType() == TokenType.LITERAL) {
        "Expected a literal token; found ${literalToken.getType()}"
    }
    return when (literalToken) {
        Token.L_INTEGER -> literalFromInteger(tokenBearer)
        Token.L_STRING, Token.L_RAW_STRING, Token.L_C_STRING, Token.L_RAW_C_STRING -> literalFromString(tokenBearer)
        Token.L_CHAR -> literalFromChar(tokenBearer)
        Token.K_TRUE, Token.K_FALSE -> literalFromBoolean(tokenBearer)
        else -> throw CompileError("Unexpected literal token: $literalToken").with(ctx)
    }
}

private fun parseUnderscore(ctx: Context): WithoutBlockExpressionNode {
    return WithoutBlockExpressionNode.UnderscoreExpressionNode
}

private fun parsePathExpression(ctx: Context): WithoutBlockExpressionNode {
    ctx.stream.rewind(1)
    return WithoutBlockExpressionNode.PathExpressionNode(PathInExpressionNode.parse(ctx))
}

private fun parsePrefixOperator(ctx: Context): WithoutBlockExpressionNode {
    val operator = ctx.prattProcessingTokenBearer!!.token
    val right = parsePrecedence(ctx, Precedence.PREFIX.value)
    return WithoutBlockExpressionNode.PrefixOperatorNode(operator, right)
}

private fun parseGroupedOrTupleExpression(ctx: Context): WithoutBlockExpressionNode {
    // '(' is already consumed
    if (ctx.peekToken() == Token.O_RPAREN) {
        ctx.stream.read() // consume ')'
        return WithoutBlockExpressionNode.TupleExpressionNode(emptyList()) // Unit tuple `()`
    }

    val firstExpr = parsePrecedence(ctx, Precedence.NONE.value)

    if (ctx.peekToken() == Token.O_COMMA) { // It's a tuple
        ctx.stream.read() // consume ','
        val elements = mutableListOf(firstExpr)
        while (ctx.peekToken() != Token.O_RPAREN) {
            elements.add(parsePrecedence(ctx, Precedence.NONE.value))
            if (ctx.peekToken() != Token.O_RPAREN) {
                putilsExpectToken(ctx, Token.O_COMMA)
            }
        }
        putilsExpectToken(ctx, Token.O_RPAREN)
        return WithoutBlockExpressionNode.TupleExpressionNode(elements)
    } else { // It was a grouped expression
        putilsExpectToken(ctx, Token.O_RPAREN)
        return firstExpr // Just return the inner expression
    }
}

private fun parseArrayExpression(ctx: Context): WithoutBlockExpressionNode {
    // '[' is already consumed
    val elements = mutableListOf<ExpressionNode>()
    var repeat: ExpressionNode = WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode(1)
    while (ctx.peekToken() != Token.O_RSQUARE) {
        elements.add(parsePrecedence(ctx, Precedence.NONE.value))
        putilsConsumeIfExistsToken(ctx, Token.O_COMMA)
        if (ctx.peekToken() == Token.O_SEMICOLON) {
            ctx.stream.consume(1)
            repeat = ExpressionNode.parse(ctx)
        }
    }
    putilsExpectToken(ctx, Token.O_RSQUARE)
    return WithoutBlockExpressionNode.ArrayExpressionNode(elements, repeat)
}

private fun parseReturnExpression(ctx: Context): WithoutBlockExpressionNode {
    // 'return' is consumed. Check if there's a value to return.
    val expr = if (getTokenPrecedence(ctx.peekToken()) != Precedence.NONE) {
        parsePrecedence(ctx, Precedence.NONE.value)
    } else {
        null
    }
    return WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode(expr)
}

private fun parseBreakExpression(ctx: Context): WithoutBlockExpressionNode {
    // 'break' is consumed.
    val expr = if (getTokenPrecedence(ctx.peekToken()) != Precedence.NONE) {
        parsePrecedence(ctx, Precedence.NONE.value)
    } else {
        null
    }
    return WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode(expr)
}

// parse left associative
private fun parseLAInfixOperator(ctx: Context, left: WithoutBlockExpressionNode): WithoutBlockExpressionNode {
    val opToken = ctx.prattProcessingTokenBearer!!.token
    val precedence = getTokenPrecedence(opToken)
    val right = parsePrecedence(ctx, precedence.value)
    return WithoutBlockExpressionNode.InfixOperatorNode(left, opToken, right)
}

// parse right associative
private fun parseRAInfixOperator(ctx: Context, left: WithoutBlockExpressionNode): WithoutBlockExpressionNode {
    val opToken = ctx.prattProcessingTokenBearer!!.token
    val precedence = getTokenPrecedence(opToken)
    val right = parsePrecedence(ctx, precedence.value - 1)
    return WithoutBlockExpressionNode.InfixOperatorNode(left, opToken, right)
}

private fun parseCallExpression(ctx: Context, callee: WithoutBlockExpressionNode): WithoutBlockExpressionNode {
    // '(' is consumed
    val args = mutableListOf<ExpressionNode>()
    if (ctx.peekToken() != Token.O_RPAREN) {
        do {
            args.add(parsePrecedence(ctx, Precedence.NONE.value))
        } while (ctx.peekToken() == Token.O_COMMA && ctx.stream.read().token == Token.O_COMMA)
    }
    putilsExpectToken(ctx, Token.O_RPAREN)
    return WithoutBlockExpressionNode.CallExpressionNode(callee, args)
}

private fun parseIndexExpression(ctx: Context, base: WithoutBlockExpressionNode): WithoutBlockExpressionNode {
    // '[' is consumed
    val index = parsePrecedence(ctx, Precedence.NONE.value)
    putilsExpectToken(ctx, Token.O_RSQUARE)
    return WithoutBlockExpressionNode.IndexExpressionNode(base, index)
}

private fun parseFieldOrTupleIndexExpression(
    ctx: Context,
    base: WithoutBlockExpressionNode
): WithoutBlockExpressionNode {
    // '.' is consumed
    val nextToken = ctx.stream.read()
    return when (nextToken.token) {
        Token.I_IDENTIFIER -> WithoutBlockExpressionNode.FieldExpressionNode(base, nextToken.raw)
        Token.L_INTEGER -> {
            // This assumes your lexer correctly identifies tuple indices as L_INTEGER
            val index = (nextToken.raw).toIntOrNull()
                ?: throw CompileError("Invalid tuple index: ${nextToken.raw}").with(ctx)
            WithoutBlockExpressionNode.TupleIndexingNode(base, index)
        }

        else -> throw CompileError("Expected identifier or integer for field access, found ${nextToken.token}").with(ctx)
    }
}

// for API usage: this version consumes a token, like all other non-pratt parsers
fun WithoutBlockExpressionNode.LiteralExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode.LiteralExpressionNode {
    val tokenBearer = ctx.stream.read()
    return when (tokenBearer.token) {
        Token.L_INTEGER -> literalFromInteger(tokenBearer)
        Token.L_STRING, Token.L_RAW_STRING, Token.L_C_STRING, Token.L_RAW_C_STRING -> literalFromString(tokenBearer)
        Token.L_CHAR -> literalFromChar(tokenBearer)
        Token.K_TRUE, Token.K_FALSE -> literalFromBoolean(tokenBearer)
        else -> throw CompileError("Unexpected literal token: ${tokenBearer.token}").with(ctx)
    }
}