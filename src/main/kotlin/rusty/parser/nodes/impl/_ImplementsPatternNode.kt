package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode
import rusty.parser.nodes.PathInExpressionNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectGroupOrTupleWithin
import rusty.parser.putils.putilsExpectToken

fun SupportingPatternNode.Companion.parse(ctx: Context): SupportingPatternNode {
    return when (ctx.peekToken()) {
        null -> throw CompileError("Pattern cannot start with EOF").with(ctx)
        Token.K_REF, Token.K_MUT -> SupportingPatternNode.IdentifierPatternNode.parse(ctx)
        Token.L_RAW_STRING, Token.L_RAW_BYTE_STRING, Token.L_RAW_C_STRING,
        Token.L_STRING, Token.L_BYTE_STRING, Token.L_C_STRING,
        Token.L_BYTE, Token.L_CHAR, Token.L_INTEGER, Token.K_TRUE, Token.K_FALSE -> SupportingPatternNode.LiteralPatternNode.parse(ctx)
        Token.O_UNDERSCORE -> SupportingPatternNode.WildcardPatternNode.parse(ctx)

        Token.O_LPAREN -> parseGroupOrTuplePattern(ctx)
        Token.K_SELF, Token.K_TYPE_SELF, Token.O_DOUBLE_COLON -> SupportingPatternNode.PathPatternNode.parse(ctx)

        Token.I_IDENTIFIER -> {
            // interpret as identifier first, then check if it's a path pattern
            ctx.tryParse("Pattern@Identifier") {
                SupportingPatternNode.IdentifierPatternNode.parse(ctx)
            } ?: run {
                SupportingPatternNode.PathPatternNode.parse(ctx)
            }
        }
        else -> TODO("Implement all other patterns")
    }
}

fun SupportingPatternNode.IdentifierPatternNode.Companion.parse(ctx: Context): SupportingPatternNode.IdentifierPatternNode {
    val isRef = putilsConsumeIfExistsToken(ctx, Token.K_REF)
    val isMut = putilsConsumeIfExistsToken(ctx, Token.K_MUT)
    val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
    val extendedByPattern = if (putilsConsumeIfExistsToken(ctx, Token.O_AT)) {
        PatternNode.parse(ctx)
    } else null
    return SupportingPatternNode.IdentifierPatternNode(
        identifier = identifier,
        isMut = isMut,
        isRef = isRef,
        extendedByPatternNode = extendedByPattern
    )
}

fun SupportingPatternNode.LiteralPatternNode.Companion.parse(ctx: Context): SupportingPatternNode.LiteralPatternNode {
    val isNegated = putilsConsumeIfExistsToken(ctx, Token.O_MINUS)
    return SupportingPatternNode.LiteralPatternNode(
        literalNode = LiteralExpressionNode.parse(ctx),
        isNegated = isNegated,
    )
}

fun SupportingPatternNode.WildcardPatternNode.parse(ctx: Context): SupportingPatternNode.WildcardPatternNode {
    putilsExpectToken(ctx, Token.O_UNDERSCORE)
    return SupportingPatternNode.WildcardPatternNode
}

fun SupportingPatternNode.PathPatternNode.Companion.parse(ctx: Context): SupportingPatternNode.PathPatternNode {
    return SupportingPatternNode.PathPatternNode(path = PathInExpressionNode.parse(ctx))
}

private fun parseGroupOrTuplePattern(ctx: Context): SupportingPatternNode {
    return putilsExpectGroupOrTupleWithin(
        ctx,
        parsingFunction = SupportingPatternNode.Companion::parse,
        tupleConstructor = SupportingPatternNode::DestructuredTuplePatternNode,
        Pair(Token.O_LPAREN, Token.O_RPAREN)
    )
}