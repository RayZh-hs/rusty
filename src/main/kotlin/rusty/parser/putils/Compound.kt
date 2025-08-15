package rusty.parser.putils

import rusty.core.CompileError
import rusty.lexer.Token

fun <T> putilsExpectListWithin(
    ctx: Context,
    parsingFunction: (Context) -> T,
    wrappingTokens: Pair<Token?, Token>,
    delimiter: Token = Token.O_COMMA,
): List<T> {
    if (wrappingTokens.first != null) {
        putilsExpectToken(ctx, wrappingTokens.first!!)
    }
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

fun <T> putilsExpectGroupOrTupleWithin(
    ctx: Context,
    parsingFunction: (Context) -> T,
    tupleConstructor: (List<T>) -> T,
    wrappingTokens: Pair<Token?, Token>,
    delimiter: Token = Token.O_COMMA,
): T {
    if (wrappingTokens.first != null) {
        putilsExpectToken(ctx, wrappingTokens.first!!)
    }
    if (putilsConsumeIfExistsToken(ctx, wrappingTokens.second)) {
        return tupleConstructor(listOf())
    }
    val firstObject = parsingFunction(ctx)
    return when (ctx.peekToken()) {
        wrappingTokens.second -> {
            // consider (obj) as a group
            ctx.stream.consume(1)
            firstObject
        }
        delimiter -> {
            ctx.stream.consume(1)
            val list = mutableListOf(firstObject)
            while (ctx.peekToken() != wrappingTokens.second) {
                list.add(parsingFunction(ctx))
            }
            ctx.stream.consume(1)
            tupleConstructor(list)
        }
        else -> throw CompileError("Items not separated by $delimiter").with(ctx)
    }
}