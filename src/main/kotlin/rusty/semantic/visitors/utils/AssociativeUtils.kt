package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.parser.nodes.support.AssociatedItemsNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import kotlin.collections.set

fun SemanticSymbol.AssociativeItem.injectAssociatedItems(ctx: Context, implScope: Scope) {
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