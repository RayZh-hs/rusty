package rusty.semantic.visitors.companions

import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SymbolTable
import rusty.semantic.visitors.utils.sequentialLookup
import java.util.Stack

// All variables and constants will be automatically pushed into the scope stack
class ScopedVariableMaintainerCompanion(ctx: Context) {
    private val scopeMaintainer = ScopeMaintainerCompanion(ctx)
    private val varSymbolStack = Stack<SymbolTable>()

    fun currentScope(): Scope = scopeMaintainer.currentScope

    fun <R> withNextScope(block: (Scope) -> R): R {
        return scopeMaintainer.withNextScope { scope ->
            varSymbolStack.push(scope.variableST)
            block(scope).afterWhich {
                varSymbolStack.pop()
            }
        }
    }

    fun declare(symbol: SemanticSymbol.Variable) {
        if (varSymbolStack.isEmpty()) {
            throw IllegalStateException("No variable symbol table available to declare symbol: $symbol")
        }
        varSymbolStack.peek().override(symbol)
    }

    fun resolve(identifier: String): SemanticSymbol? {
        var cursor: Scope = currentScope()
        for (i in varSymbolStack.size - 1 downTo 0) {
            val symbol = varSymbolStack[i].resolve(identifier)
            if (symbol != null) {
                return symbol
            }
            if (cursor.kind == Scope.ScopeKind.FunctionParams)  // this marks the beginning of a function scope
                break   // this ensures that functions form no closures
            cursor = cursor.parent ?: break
        }
        // if not found, look for constants in the whole scope chain
        val resolved = sequentialLookup(identifier, currentScope(), {it.variableST})
        if (resolved != null && resolved.symbol is SemanticSymbol.Const)
            return resolved.symbol
        return null
    }

    @Suppress("unused")
    fun resolveConst(identifier: String): SemanticSymbol.Const? {
        return resolve(identifier) as? SemanticSymbol.Const
    }

    @Suppress("unused")
    fun resolveVariable(identifier: String): SemanticSymbol.Variable? {
        return resolve(identifier) as? SemanticSymbol.Variable
    }
}