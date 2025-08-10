package rusty.parser.putils

import rusty.lexer.Token

fun putilsContextHasHitEOF(ctx: Context): Boolean {
    return ctx.stream.atEnd()
}

fun putilsContextHasHitToken(tok: Token): (Context) -> Boolean {
    return { ctx: Context ->
        if (ctx.stream.atEnd()) {
            true
        } else if (ctx.stream.peek().token == tok) {
            ctx.stream.consume(1)
            true
        } else {
            false
        }
    }
}
