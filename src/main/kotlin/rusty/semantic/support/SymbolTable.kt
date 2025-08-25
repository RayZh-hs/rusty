package rusty.semantic.support

import rusty.core.CompileError

class SymbolTable {
    val symbols: MutableMap<String, Symbol> = mutableMapOf()

    fun declare(symbol: Symbol): Symbol {
        if (symbols.containsKey(symbol.identifier))
            throw CompileError("Symbol '${symbol.identifier}' is already defined in the scope: $this").at(symbol.definedAt?.pointer)
        symbols[symbol.identifier] = symbol
        return symbol
    }

    fun resolve(identifier: String): Symbol? {
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