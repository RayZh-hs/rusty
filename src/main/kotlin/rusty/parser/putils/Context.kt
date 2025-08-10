package rusty.parser.putils

import rusty.lexer.Token
import rusty.parser.TokenStream
import java.util.Stack

// A parsing context implementation
data class Context(
    val stream: TokenStream,
    val parseStack: Stack<ParseStackItem> = Stack(),
    val prattStack: Stack<PrattStackItem> = Stack(),
    var attemptedParseObjectSet: MutableSet<Pair<Int, String>> = mutableSetOf(),
    var failedParseObjectSet: MutableSet<Pair<Int, String>> = mutableSetOf(),
) {
    data class ParseStackItem(val lineNumber: Int?, val name: String)
    data class PrattStackItem(val rbp: Int, val name: String)

    fun hasBeenCalled(name: String): Boolean {
        return attemptedParseObjectSet.contains(Pair(stream.cur, name)) || failedParseObjectSet.contains(Pair(stream.cur, name))
    }

    inline fun <T> callMe(name: String, block: () -> T): T {
        parseStack.push(ParseStackItem(lineNumber = stream.peekOrNull()?.lineNumber, name))
        try {
            return block()
        } finally {
            parseStack.pop()
        }
    }

    inline fun <T> tryParse(name: String, block: () -> T): T? {
        if (hasBeenCalled(name))
            return null
        stream.pushCursor(name)
        attemptedParseObjectSet.add(Pair(stream.cur, name))
        var whatToReturn: T?
        try {
            whatToReturn = block()
        } catch (t: Throwable) {
            failedParseObjectSet.add(Pair(stream.cur, name))
            whatToReturn = null
        } finally {
            attemptedParseObjectSet.remove(Pair(stream.cur, name))
            stream.popCursor()
        }
        return whatToReturn
    }

    fun peekToken(): Token? = stream.peekOrNull()?.token
}