package rusty.semantic.visitors.companions

import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
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

    fun getSelfType(): SemanticType? {
        return when (val self = getSelf()) {
            is SemanticSymbol.Struct -> self.definesType
            is SemanticSymbol.Enum -> self.definesType
            is SemanticSymbol.Trait -> self.definesType
            else -> null
        }
    }

    override fun toString(): String {
        return "SelfResolverCompanion(currentSelf=${getSelf()}, selfStack=$selfStack)"
    }
}