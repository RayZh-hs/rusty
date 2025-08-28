package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType

// For compatibility: Turn
fun destructPattern(pattern: PatternNode): SupportingPatternNode {
    if (pattern.patternNodes.size != 1)
        throw CompileError("Patterns should contain exactly one pattern-no-top-alt item")
            .with(pattern).at(pattern.pointer)
    return pattern.patternNodes[0]
}

// A simplified yet scope-aware check for whether a pattern is irrefutable
fun isPatternIrrefutable(pattern: SupportingPatternNode, scope: Scope): Boolean {
    when (pattern) {
        is SupportingPatternNode.WildcardPatternNode -> return true
        is SupportingPatternNode.IdentifierPatternNode -> {
            if (pattern.extendedByPatternNode != null) return false
            val sym = sequentialLookup(pattern.identifier, scope, { it.variableST })
            if (sym == null || sym.symbol !is SemanticSymbol.Const) {
                return true
            } else {
                // constants cannot be overridden
                return false
            }
        }
        is SupportingPatternNode.LiteralPatternNode -> return false
        is SupportingPatternNode.DestructuredTuplePatternNode -> {
            return pattern.tuple.all { isPatternIrrefutable(it, scope) }
        }
        is SupportingPatternNode.PathPatternNode -> return false    // a path signifies an enum or struct ie. a refutable pattern
    }
}

fun isPatternRefutable(pattern: SupportingPatternNode, scope: Scope): Boolean = !isPatternIrrefutable(pattern, scope)

fun extractSymbolsFromTypedPattern(pattern: PatternNode, semanticType: SemanticType, scope: Scope): List<SemanticSymbol> {
    val supportingPattern = destructPattern(pattern)
    TODO()
}