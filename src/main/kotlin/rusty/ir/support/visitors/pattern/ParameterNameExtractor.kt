package rusty.ir.support.visitors.pattern

import rusty.ir.support.Name
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.visitors.utils.extractSymbolsFromTypedPattern

/**
 * Extracts user-friendly parameter names from semantic function metadata.
 * We reuse the same pattern walker as semantic analysis to stay consistent.
 */
class ParameterNameExtractor(
    private val scope: Scope,
) {
    fun orderedParamNames(function: SemanticSymbol.Function): List<String> {
        val names = mutableListOf<String>()
        function.funcParams.getOrNull()?.forEachIndexed { index, param ->
            val symbols = extractSymbolsFromTypedPattern(param.pattern, param.type.get(), scope)
            val primary = symbols.firstOrNull()?.identifier ?: "arg$index"
            names += Name.ofVariable(
                SemanticSymbol.Variable(
                    identifier = primary,
                    definedAt = param.pattern,
                ),
                allowSerial = false
            ).identifier
        }
        return names
    }
}
