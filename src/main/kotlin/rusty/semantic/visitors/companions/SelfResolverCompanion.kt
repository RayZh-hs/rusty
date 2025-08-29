package rusty.semantic.visitors.companions

import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.SemanticSymbol
import java.util.Stack

class SelfResolverCompanion {
    val selfStack: Stack<SemanticSymbol> = Stack()

    fun <R> withinSymbol(symbol: SemanticSymbol, block: () -> R): R {
        println("Entering self context: $symbol")
        selfStack.push(symbol)
        return block().afterWhich {
            println("Exiting self context: $symbol")
            selfStack.pop()
        }
    }

    fun getSelf(): SemanticSymbol? = selfStack.lastOrNull()

    override fun toString(): String {
        return "SelfResolverCompanion(currentSelf=${getSelf()}, selfStack=$selfStack)"
    }
}