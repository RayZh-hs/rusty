package rusty.parser.putils

import rusty.lexer.Token
import rusty.lexer.TokenBearer
import rusty.parser.TokenStream
import java.util.Stack

// A parsing context implementation
data class Context(
    val stream: TokenStream,
    val parseStack: Stack<ParseStackItem> = Stack(),
    var attemptedParseObjectSet: MutableSet<Pair<Int, String>> = mutableSetOf(),
    var failedParseObjectSet: MutableSet<Pair<Int, String>> = mutableSetOf(),
    var prattProcessingTokenBearer: TokenBearer? = null,
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
        val currentCursor = stream.cur
        attemptedParseObjectSet.add(Pair(currentCursor, name))
        var whatToReturn: T? = null
        try {
            whatToReturn = block()
        } catch (t: Throwable) {
            failedParseObjectSet.add(Pair(currentCursor, name))
            whatToReturn = null
        } finally {
            attemptedParseObjectSet.remove(Pair(currentCursor, name))
            when (whatToReturn) {
                null -> stream.popCursor()
                else -> stream.popCursorWithApply()
            }
        }
        return whatToReturn
    }

    fun peekToken(): Token? = stream.peekOrNull()?.token
}