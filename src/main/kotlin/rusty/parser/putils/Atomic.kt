package rusty.parser.putils

import rusty.core.CompileError
import rusty.lexer.Token

fun putilsExpectToken(ctx: Context, token: Token): String {
    val tokenBearer = ctx.stream.readOrNull()
    if (tokenBearer == null)
        throw CompileError("Parsec: End of stream when expecting token $token")
    else if (tokenBearer.token != token)
        throw CompileError("Unexpected token '${tokenBearer.raw}' on line ${tokenBearer.lineNumber}").with(ctx)
    return tokenBearer.raw
}

fun putilsConsumeIfExistsToken(ctx: Context, token: Token) {
    val tokenBearer = ctx.stream.readOrNull()
    if (tokenBearer?.token == token)
        ctx.stream.consume(1)
}