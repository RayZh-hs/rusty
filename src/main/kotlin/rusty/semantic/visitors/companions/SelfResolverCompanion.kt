package rusty.semantic.visitors.companions

import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.SemanticSymbol
import java.util.Stack

class SelfResolverCompanion {
    val selfStack: Stack<SemanticSymbol> = Stack()

    fun <R> withinSymbol(symbol: SemanticSymbol, block: () -> R): R {
        selfStack.push(symbol)
        return block().afterWhich {
            selfStack.pop()
        }
    }

    fun getSelf(): SemanticSymbol? = selfStack.lastOrNull()
}