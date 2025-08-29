package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode
import rusty.parser.nodes.PathInExpressionNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.support.StructExprFieldNode
import rusty.parser.nodes.utils.literalFromBoolean
import rusty.parser.nodes.utils.literalFromChar
import rusty.parser.nodes.utils.literalFromInteger
import rusty.parser.nodes.utils.literalFromString
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectListWithin
import rusty.parser.putils.putilsExpectToken

val WithoutBlockExpressionNode.Companion.name get() = "WithoutBlockExpression"

fun WithoutBlockExpressionNode.Companion.parse(ctx: Context): ExpressionNode {
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

private typealias NudParselet = (Context) -> ExpressionNode
private typealias LedParselet = (Context, ExpressionNode) -> ExpressionNode

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
    Token.I_IDENTIFIER to ::parsePathOrStructExpression,
    Token.O_DOUBLE_COLON to ::parsePathOrStructExpression,
    Token.K_SELF to ::parsePathOrStructExpression,
    Token.K_TYPE_SELF to ::parsePathOrStructExpression,

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
    Token.K_CONTINUE to ::parseContinueExpression,

    // Block expressions are handled here
    Token.O_LCURL to ::parseBlockExpression,
    Token.K_CONST to ::parseBlockExpression,
    Token.K_LOOP to ::parseBlockExpression,
    Token.K_WHILE to ::parseBlockExpression,
    Token.K_IF to ::parseBlockExpression,
    Token.K_MATCH to ::parseBlockExpression,
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
    Token.O_DOT to ::parseFieldOrTupleIndexExpression,
    // Cast operator: left 'as' Type
    Token.K_AS to ::parseTypeCastExpression
)

private fun parsePrecedence(ctx: Context, precedence: Int): ExpressionNode {
    val startToken = ctx.peekToken()
        ?: throw CompileError("Unexpected EOF when expecting start of expression").with(ctx).at(ctx.peekPointer())
    val nud = nudParselets[startToken]
        ?: throw CompileError("Invalid start of expression: $startToken").with(ctx).at(ctx.peekPointer())

    var left = nud(ctx)

    while (true) {
        val nextToken = ctx.peekToken()
        val nextPrec = getTokenPrecedence(nextToken)
        if (precedence >= nextPrec.value) break
        val led = ledParselets[nextToken] ?: break // Not an infix/postfix operator
        left = led(ctx, left)
    }
    return left
}

private fun parseLiteral(ctx: Context): WithoutBlockExpressionNode {
    val tokenBearer = ctx.stream.read()
    return when (val literalToken = tokenBearer.token) {
        Token.L_INTEGER -> literalFromInteger(tokenBearer)
        Token.L_STRING, Token.L_RAW_STRING, Token.L_C_STRING, Token.L_RAW_C_STRING -> literalFromString(tokenBearer)
        Token.L_CHAR -> literalFromChar(tokenBearer)
        Token.K_TRUE, Token.K_FALSE -> literalFromBoolean(tokenBearer)
        else -> throw CompileError("Unexpected literal token: $literalToken").with(ctx).at(ctx.peekPointer())
    }
}

private fun parseUnderscore(ctx: Context): WithoutBlockExpressionNode {
    putilsExpectToken(ctx, Token.O_UNDERSCORE)
    return WithoutBlockExpressionNode.UnderscoreExpressionNode(ctx.topPointer())
}

private fun parsePathOrStructExpression(ctx: Context): WithoutBlockExpressionNode {
    val path = PathInExpressionNode.parse(ctx)
    if (!ctx.isStructEnabled()) {
        return WithoutBlockExpressionNode.PathExpressionNode(path, ctx.topPointer())
    }
    return when (ctx.peekToken()) {
        Token.O_LCURL -> {
            val fields = putilsExpectListWithin(
                ctx,
                parsingFunction = StructExprFieldNode::parse,
                wrappingTokens = Pair(Token.O_LCURL, Token.O_RCURL)
            )
            WithoutBlockExpressionNode.StructExpressionNode(path, fields, ctx.topPointer())
        }

        else -> WithoutBlockExpressionNode.PathExpressionNode(path, ctx.topPointer())
    }
}

private fun parsePrefixOperator(ctx: Context): WithoutBlockExpressionNode {
    val operator = ctx.stream.read().token
    val right = parsePrecedence(ctx, Precedence.PREFIX.value)
    return WithoutBlockExpressionNode.PrefixOperatorNode(operator, right, ctx.topPointer())
}

private fun parseGroupedOrTupleExpression(ctx: Context): ExpressionNode {
    putilsExpectToken(ctx, Token.O_LPAREN)
    if (ctx.peekToken() == Token.O_RPAREN) {
        ctx.stream.read() // consume ')'
        return WithoutBlockExpressionNode.TupleExpressionNode(emptyList(), ctx.topPointer())
    }
    val firstExpr = parsePrecedence(ctx, Precedence.NONE.value)
    if (ctx.peekToken() == Token.O_COMMA) {
        ctx.stream.read() // consume ','
        val elements = mutableListOf<ExpressionNode>(firstExpr)
        while (ctx.peekToken() != Token.O_RPAREN) {
            elements.add(parsePrecedence(ctx, Precedence.NONE.value))
            if (ctx.peekToken() != Token.O_RPAREN) {
                putilsExpectToken(ctx, Token.O_COMMA)
            }
        }
        putilsExpectToken(ctx, Token.O_RPAREN)
        return WithoutBlockExpressionNode.TupleExpressionNode(elements, ctx.topPointer())
    }
    putilsExpectToken(ctx, Token.O_RPAREN)
    return firstExpr
}

private fun parseArrayExpression(ctx: Context): WithoutBlockExpressionNode {
    putilsExpectToken(ctx, Token.O_LSQUARE)
    val elements = mutableListOf<ExpressionNode>()
    var repeat: ExpressionNode = WithoutBlockExpressionNode.LiteralExpressionNode.I32LiteralNode(1, ctx.topPointer())
    while (ctx.peekToken() != Token.O_RSQUARE) {
        elements.add(parsePrecedence(ctx, Precedence.NONE.value))
        putilsConsumeIfExistsToken(ctx, Token.O_COMMA)
        if (ctx.peekToken() == Token.O_SEMICOLON) {
            ctx.stream.consume(1)
            repeat = ExpressionNode.parse(ctx)
        }
    }
    putilsExpectToken(ctx, Token.O_RSQUARE)
    return WithoutBlockExpressionNode.ArrayExpressionNode(elements, repeat, ctx.topPointer())
}

private fun parseReturnExpression(ctx: Context): WithoutBlockExpressionNode {
    putilsExpectToken(ctx, Token.K_RETURN)
    val expr = when (ctx.peekToken()) {
        Token.O_SEMICOLON -> null
        else -> parsePrecedence(ctx, Precedence.NONE.value)
    }
    return WithoutBlockExpressionNode.ControlFlowExpressionNode.ReturnExpressionNode(expr, ctx.topPointer())
}

private fun parseBreakExpression(ctx: Context): WithoutBlockExpressionNode {
    putilsExpectToken(ctx, Token.K_BREAK)
    val expr = when (ctx.peekToken()) {
        Token.O_SEMICOLON -> null
        else -> parsePrecedence(ctx, Precedence.NONE.value)
    }
    return WithoutBlockExpressionNode.ControlFlowExpressionNode.BreakExpressionNode(expr, ctx.topPointer())
}

private fun parseContinueExpression(ctx: Context): WithoutBlockExpressionNode {
    putilsExpectToken(ctx, Token.K_CONTINUE)
    return WithoutBlockExpressionNode.ControlFlowExpressionNode.ContinueExpressionNode(ctx.topPointer())
}

private fun parseBlockExpression(ctx: Context): ExpressionNode {
    return ExpressionNode.WithBlockExpressionNode.parse(ctx)
}

private fun parseLAInfixOperator(ctx: Context, left: ExpressionNode): ExpressionNode {
    val opToken = ctx.stream.read().token
    val precedence = getTokenPrecedence(opToken)
    val right = parsePrecedence(ctx, precedence.value)
    return WithoutBlockExpressionNode.InfixOperatorNode(left, opToken, right, ctx.topPointer())
}

private fun parseRAInfixOperator(ctx: Context, left: ExpressionNode): ExpressionNode {
    val opToken = ctx.stream.read().token
    val precedence = getTokenPrecedence(opToken)
    val right = parsePrecedence(ctx, precedence.value - 1)
    return WithoutBlockExpressionNode.InfixOperatorNode(left, opToken, right, ctx.topPointer())
}

private fun parseTypeCastExpression(ctx: Context, left: ExpressionNode): ExpressionNode {
    // consume 'as'
    putilsExpectToken(ctx, Token.K_AS)
    val targetType = TypeNode.parse(ctx)
    return WithoutBlockExpressionNode.TypeCastExpressionNode(left, targetType, ctx.topPointer())
}

private fun parseCallExpression(ctx: Context, callee: ExpressionNode): ExpressionNode {
    putilsExpectToken(ctx, Token.O_LPAREN)
    val args = mutableListOf<ExpressionNode>()
    if (ctx.peekToken() != Token.O_RPAREN) {
        args.add(parsePrecedence(ctx, Precedence.NONE.value))
        while (ctx.peekToken() == Token.O_COMMA) {
            ctx.stream.read() // consume comma
            args.add(parsePrecedence(ctx, Precedence.NONE.value))
        }
    }
    putilsExpectToken(ctx, Token.O_RPAREN)
    return WithoutBlockExpressionNode.CallExpressionNode(callee, args, ctx.topPointer())
}

private fun parseIndexExpression(ctx: Context, base: ExpressionNode): ExpressionNode {
    putilsExpectToken(ctx, Token.O_LSQUARE)
    val index = parsePrecedence(ctx, Precedence.NONE.value)
    putilsExpectToken(ctx, Token.O_RSQUARE)
    return WithoutBlockExpressionNode.IndexExpressionNode(base, index, ctx.topPointer())
}

private fun parseFieldOrTupleIndexExpression(
    ctx: Context,
    base: ExpressionNode
): ExpressionNode {
    putilsExpectToken(ctx, Token.O_DOT)
    val nextToken = ctx.stream.read()
    return when (nextToken.token) {
        Token.I_IDENTIFIER -> WithoutBlockExpressionNode.FieldExpressionNode(base, nextToken.raw, ctx.topPointer())
        Token.L_INTEGER -> {
            val index =
                nextToken.raw.toIntOrNull() ?: throw CompileError("Invalid tuple index: ${nextToken.raw}").with(ctx)
                    .at(ctx.peekPointer())
            WithoutBlockExpressionNode.TupleIndexingNode(base, index, ctx.topPointer())
        }

        else -> throw CompileError("Expected identifier or integer for field access, found ${nextToken.token}").with(ctx)
            .at(ctx.peekPointer())
    }
}

fun WithoutBlockExpressionNode.LiteralExpressionNode.Companion.parse(ctx: Context): WithoutBlockExpressionNode.LiteralExpressionNode {
    val tokenBearer = ctx.stream.read()
    return when (tokenBearer.token) {
        Token.L_INTEGER -> literalFromInteger(tokenBearer)
        Token.L_STRING, Token.L_RAW_STRING, Token.L_C_STRING, Token.L_RAW_C_STRING -> literalFromString(tokenBearer)
        Token.L_CHAR -> literalFromChar(tokenBearer)
        Token.K_TRUE, Token.K_FALSE -> literalFromBoolean(tokenBearer)
        else -> throw CompileError("Unexpected literal token: ${tokenBearer.token}").with(ctx).at(ctx.peekPointer())
    }
}