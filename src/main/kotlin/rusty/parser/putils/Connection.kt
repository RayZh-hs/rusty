package rusty.parser.putils

import rusty.core.CompileError

fun <T> putilsOptional(ctx: Context, parsingFun: (Context) -> T, terminationFun: (Context) -> Boolean): T? {
    if (!terminationFun(ctx)) {
        return parsingFun(ctx)
    }
    return null
}

fun <T> putilsZeroOrMore(ctx: Context, parsingFun: (Context) -> T, terminationFun: (Context) -> Boolean): List<T> {
    val ret = mutableListOf<T>()
    if (!terminationFun(ctx)) {
        ret.add(parsingFun(ctx))
    }
    return ret
}

fun <T> putilsOneOrMore(ctx: Context, parsingFun: (Context) -> T, terminationFun: (Context) -> Boolean): List<T> {
    val ret = mutableListOf<T>()
    if (!terminationFun(ctx)) {
        ret.add(parsingFun(ctx))
    }
    if (ret.size == 0)
        throw CompileError("Parsec: No arguments identified by + syntax at line ${ctx.stream.peek().lineNumber}")
    return ret
}