package rusty.parser.putils

import rusty.core.CompileError
import rusty.lexer.Token

fun <T> putilsExpectListWithin(
    ctx: Context,
    parsingFunction: (Context) -> T,
    wrappingTokens: Pair<Token, Token>,
    delimiter: Token = Token.O_COMMA,
): List<T> {
    putilsExpectToken(ctx, wrappingTokens.first)
    val result = mutableListOf<T>()
    var hasDelimiter = true
    while (ctx.peekToken() != wrappingTokens.second) {
        if (!hasDelimiter)
            throw CompileError("Items not separated by $delimiter").with(ctx)
        result.add(parsingFunction(ctx))
        hasDelimiter = putilsConsumeIfExistsToken(ctx, delimiter)
    }
    return result
}