package rusty.parser.nodes.impl

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.parser.nodes.SupportingPatternNode.IdentifierPatternNode
import rusty.parser.nodes.SupportingPatternNode.LiteralPatternNode
import rusty.parser.nodes.SupportingPatternNode.WildcardPatternNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken

fun SupportingPatternNode.Companion.parse(ctx: Context): SupportingPatternNode {
    return when (ctx.peekToken()) {
        null -> throw CompileError("Pattern cannot start with EOF").with(ctx)
        Token.K_REF, Token.K_MUT, Token.I_IDENTIFIER -> IdentifierPatternNode.parse(ctx)
        Token.L_RAW_STRING, Token.L_RAW_BYTE_STRING, Token.L_RAW_C_STRING,
        Token.L_STRING, Token.L_BYTE_STRING, Token.L_C_STRING,
        Token.L_BYTE, Token.L_CHAR, Token.L_INTEGER, Token.K_TRUE, Token.K_FALSE -> LiteralPatternNode.parse(ctx)
        Token.O_UNDERSCORE -> WildcardPatternNode.parse(ctx)
        else -> TODO("Implement all other patterns")
    }
}

fun IdentifierPatternNode.Companion.parse(ctx: Context): IdentifierPatternNode {
    val isRef = putilsConsumeIfExistsToken(ctx, Token.K_REF)
    val isMut = putilsConsumeIfExistsToken(ctx, Token.K_MUT)
    val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
    val extendedByPattern = if (putilsConsumeIfExistsToken(ctx, Token.O_AT)) {
        PatternNode.parse(ctx)
    } else null
    return IdentifierPatternNode(
        identifier = identifier,
        isMut = isMut,
        isRef = isRef,
        extendedByPatternNode = extendedByPattern
    )
}

fun LiteralPatternNode.Companion.parse(ctx: Context): LiteralPatternNode {
    val isNegated = putilsConsumeIfExistsToken(ctx, Token.O_MINUS)
    return LiteralPatternNode(
        literalNode = LiteralExpressionNode.parse(ctx),
        isNegated = isNegated,
    )
}

fun WildcardPatternNode.parse(ctx: Context): WildcardPatternNode {
    putilsExpectToken(ctx, Token.O_UNDERSCORE)
    return WildcardPatternNode
}