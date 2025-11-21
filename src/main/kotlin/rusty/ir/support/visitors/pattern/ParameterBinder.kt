package rusty.ir.support.visitors.pattern

import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern

class ParameterBinder(private val scope: Scope) {
    fun orderedSymbols(function: SemanticSymbol.Function): List<SemanticSymbol.Variable> {
        val params = mutableListOf<SemanticSymbol.Variable>()
        function.funcParams.getOrNull()?.forEach { param ->
            val identifiers = extractSymbolsFromTypedPattern(param.pattern, param.type.get(), scope)
                .map { it.identifier }
            identifiers.forEach { id ->
                val resolved = scope.variableST.resolve(id) as? SemanticSymbol.Variable
                    ?: throw IllegalStateException("Parameter symbol '$id' not found in scope")
                params += resolved
            }
        }
        return params
    }
}
