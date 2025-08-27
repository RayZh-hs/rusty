package rusty.semantic.support

import rusty.core.CompileError

class SymbolTable {
    val symbols: MutableMap<String, SemanticSymbol> = mutableMapOf()

    fun declare(symbol: SemanticSymbol): SemanticSymbol {
        if (symbols.containsKey(symbol.identifier))
            throw CompileError("Symbol '${symbol.identifier}' is already defined in the scope: $this").at(symbol.definedAt?.pointer)
        symbols[symbol.identifier] = symbol
        return symbol
    }

    fun resolve(identifier: String): SemanticSymbol? {
        return symbols[identifier]
    }

    fun assert(identifier: String) {
        if (!symbols.containsKey(identifier))
            throw CompileError("Symbol '$identifier' is not defined in the scope: $this")
    }

    fun exists(identifier: String): Boolean {
        return symbols.containsKey(identifier)
    }

    override fun toString(): String {
        return "SymbolTable(symbols=$symbols)"
    }
}