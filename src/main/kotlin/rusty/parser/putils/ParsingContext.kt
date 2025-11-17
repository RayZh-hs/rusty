package rusty.parser.putils

import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.TokenStream
import java.util.Stack

// A parsing context implementation
data class ParsingContext(
    val stream: TokenStream,
    val flags: Flags = Flags(),
    val parseStack: Stack<ParseStackItem> = Stack(),
    val structEnabledStack: Stack<Boolean> = Stack(),
    var attemptedParseObjectSet: MutableSet<Pair<Int, String>> = mutableSetOf(),
    var failedParseObjectSet: MutableSet<Pair<Int, String>> = mutableSetOf(),
) {
    data class ParseStackItem(val pointer: CompilerPointer, val name: String)
    data class PrattStackItem(val rbp: Int, val name: String)

    init {
        structEnabledStack.push(true)
    }

    fun hasBeenCalled(name: String): Boolean {
        return attemptedParseObjectSet.contains(Pair(stream.cur, name)) || failedParseObjectSet.contains(Pair(stream.cur, name))
    }

    inline fun <T> callMe(name: String, enable_stack: Boolean? = null, block: () -> T): T {
        parseStack.push(ParseStackItem(pointer = peekPointer(), name))
        if (enable_stack != null) {
            structEnabledStack.push(enable_stack)
        }
        try {
            return block()
        } finally {
            if (enable_stack != null) {
                structEnabledStack.pop()
            }
            parseStack.pop()
        }
    }

    inline fun <T> withEnableStruct(enable: Boolean, block: () -> T): T {
        structEnabledStack.push(enable)
        try {
            return block()
        } finally {
            structEnabledStack.pop()
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
    fun peekPointer(): CompilerPointer {
        val pos = stream.cur.coerceIn(0, stream.size - 1)
        val bearer = stream.peekAt(pos)
        return CompilerPointer(bearer.lineNumber, bearer.columnNumber)
    }

    fun topPointer(): CompilerPointer {
        return if (parseStack.isEmpty()) {
            peekPointer()
        } else {
            parseStack.peek().pointer
        }
    }

    fun isStructEnabled(): Boolean {
        return structEnabledStack.isNotEmpty() && structEnabledStack.peek()
    }
}