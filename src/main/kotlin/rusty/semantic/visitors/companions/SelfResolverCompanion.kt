package rusty.semantic.visitors.companions

import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import java.util.Stack

/**
 * Companion to manage 'self' context stack during semantic visiting.
 * Additionally caches the resolved self type for quick access.
 */
class SelfResolverCompanion {
    val selfStack: Stack<SemanticSymbol> = Stack()
    private var cachedSelfType: SemanticType? = null
    private var cachedSelfSymbol: SemanticSymbol? = null

    fun <R> withinSymbol(symbol: SemanticSymbol, block: () -> R): R {
        selfStack.push(symbol)
        // update cache
        cachedSelfSymbol = symbol
        cachedSelfType = when (symbol) {
            is SemanticSymbol.Struct -> symbol.definesType
            is SemanticSymbol.Enum -> symbol.definesType
            is SemanticSymbol.Trait -> symbol.definesType
            else -> null
        }
        return block().afterWhich {
            selfStack.pop()
            // refresh cache based on remaining stack top
            cachedSelfSymbol = selfStack.lastOrNull()
            cachedSelfType = when (val s = cachedSelfSymbol) {
                is SemanticSymbol.Struct -> s.definesType
                is SemanticSymbol.Enum -> s.definesType
                is SemanticSymbol.Trait -> s.definesType
                else -> null
            }
        }
    }

    fun getSelf(): SemanticSymbol? = cachedSelfSymbol ?: selfStack.lastOrNull()

    fun getSelfType(): SemanticType? = cachedSelfType ?: when (val s = getSelf()) {
        is SemanticSymbol.Struct -> s.definesType
        is SemanticSymbol.Enum -> s.definesType
        is SemanticSymbol.Trait -> s.definesType
        else -> null
    }

    override fun toString(): String {
        return "SelfResolverCompanion(currentSelf=${getSelf()}, selfStack=$selfStack)"
    }
}