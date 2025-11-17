package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import kotlin.collections.set

@Deprecated("Use the other overload that takes a list of symbols instead")
fun SemanticSymbol.AssociativeItem.injectAssociatedItems(ctx: SemanticContext, implScope: Scope) {
    val implFunctions = implScope.functionST.symbols.mapValues { (_, symbol) ->
        symbol as? SemanticSymbol.Function
            ?: throw CompileError("Expected function symbol, found $symbol").with(ctx).at(symbol.definedAt?.pointer)
    }
    val implConstants = implScope.variableST.symbols.mapValues { (_, symbol) ->
        symbol as? SemanticSymbol.Const
            ?: throw CompileError("Expected constant symbol, found $symbol").with(ctx).at(symbol.definedAt?.pointer)
    }
    // Check for duplicates
    implFunctions.forEach { (key, funcSymbol) ->
        if (this.functions.containsKey(key)) {
            throw CompileError("Duplicate function $key in impl for struct $this")
                .with(ctx).at(funcSymbol.definedAt?.pointer)
        }
    }
    implConstants.forEach { (key, constSymbol) ->
        if (this.constants.containsKey(key)) {
            throw CompileError("Duplicate constant $key in impl for struct $this")
                .with(ctx).at(constSymbol.definedAt?.pointer)
        }
    }
    // Inject into the symbol
    this.functions += implFunctions.toMap()
    this.constants += implConstants.toMap()
}

fun SemanticSymbol.AssociativeItem.injectAssociatedItems(symbols: List<SemanticSymbol>) {
    for (sym in symbols) {
        when (sym) {
            is SemanticSymbol.Function -> {
                if (this.functions.containsKey(sym.identifier)) {
                    throw CompileError("Duplicate function ${sym.identifier} in impl for struct $this")
                        .at(sym.definedAt?.pointer)
                }
                this.functions[sym.identifier] = sym
            }

            is SemanticSymbol.Const -> {
                if (this.constants.containsKey(sym.identifier)) {
                    throw CompileError("Duplicate constant ${sym.identifier} in impl for struct $this")
                        .at(sym.definedAt?.pointer)
                }
                this.constants[sym.identifier] = sym
            }

            else -> {
                throw CompileError("Expected function or constant symbol, found $sym")
                    .at(sym.definedAt?.pointer)
            }
        }
    }
}
