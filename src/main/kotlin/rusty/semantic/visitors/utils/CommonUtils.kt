package rusty.semantic.visitors.utils

import rusty.semantic.support.Scope
import rusty.semantic.support.Symbol
import rusty.semantic.support.SymbolTable

data class SymbolAndScope(val symbol: Symbol, val scope: Scope)

fun sequentialLookup(identifier: String, scope: Scope, symbolTableGetter: (Scope) -> SymbolTable): SymbolAndScope? {
    var scopePointer: Scope? = scope
    while (scopePointer != null) {
        val symbol = symbolTableGetter(scopePointer).symbols[identifier]
        if (symbol != null) {
            return SymbolAndScope(symbol, scopePointer)
        }
        scopePointer = scopePointer.parent
    }
    return null
}