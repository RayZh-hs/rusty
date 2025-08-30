package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern

class FunctionParamsDeclareVisitor(ctx: Context) : ScopeAwareVisitorBase(ctx) {
    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        val scope = currentScope()
        val symbol = (scope.functionST.resolve(node.identifier) as? SemanticSymbol.Function)
            ?: throw CompileError("Unresolved function: ${node.identifier}")
                .with(node).with(scope).at(node.pointer)
        scopeMaintainer.withNextScope { definitionScope ->
            symbol.selfParam.getOrNull()?.let {
                definitionScope.variableST.declare(it.symbol.get())
            }
            symbol.funcParams.getOrNull()?.forEach { param ->
                val iteratedPatterns = extractSymbolsFromTypedPattern(param.pattern, param.type.get(), currentScope())
                iteratedPatterns.forEach {
                    definitionScope.variableST.declare(it)
                }
            }
            super.visitFunctionInternal(node)
        }
    }
}