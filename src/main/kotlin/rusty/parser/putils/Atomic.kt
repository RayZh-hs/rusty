package rusty.parser.putils

import rusty.core.CompileError
import rusty.lexer.Token

fun putilsExpectToken(ctx: Context, token: Token): String {
    val tokenBearer = ctx.stream.readOrNull()
    if (tokenBearer == null)
        throw CompileError("Parsec: End of stream when expecting token $token")
    else if (tokenBearer.token != token)
    throw CompileError("Unexpected token '${tokenBearer.raw}' at ${tokenBearer.lineNumber}:${tokenBearer.columnNumber}").with(ctx)
    return tokenBearer.raw
}

fun putilsConsumeIfExistsToken(ctx: Context, token: Token): Boolean {
    val tokenBearer = ctx.stream.peekOrNull()
    if (tokenBearer?.token == token) {
        ctx.stream.consume(1)
        return true
    }
    return false
}