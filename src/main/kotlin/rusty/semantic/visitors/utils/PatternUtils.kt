package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.core.utils.Slot

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

fun extractSymbolsFromTypedPattern(pattern: PatternNode, semanticType: SemanticType, scope: Scope): List<SemanticSymbol.Variable> {
    val supportingPattern = destructPattern(pattern)
    fun extract(p: SupportingPatternNode, ty: SemanticType): List<SemanticSymbol.Variable> {
        return when (p) {
            is SupportingPatternNode.WildcardPatternNode -> emptyList()
            is SupportingPatternNode.LiteralPatternNode -> emptyList()
            is SupportingPatternNode.PathPatternNode -> emptyList() // enum/const patterns bind no names in this spec

            is SupportingPatternNode.IdentifierPatternNode -> {
                // Guard against binding over existing consts anywhere in visible scopes
                val existing = sequentialLookup(p.identifier, scope, { it.variableST })
                if (existing?.symbol is SemanticSymbol.Const)
                    throw CompileError("Identifier '${p.identifier}' clashes with existing constant").with(p)

                val boundType = if (p.isRef) {
                    // ref [mut] x -> & [mut] ty
                    SemanticType.ReferenceType(type = Slot(ty), isMutable = Slot(p.isMut))
                } else ty

                val sym = SemanticSymbol.Variable(
                    identifier = p.identifier,
                    definedAt = p,
                ).also {
                    // If it's a reference binding, 'mut' applies to the reference; the binding itself isn't reassignable.
                    // Otherwise, 'mut' applies to the variable's reassignability.
                    it.mutable.set(if (p.isRef) false else p.isMut)
                    if (boundType != SemanticType.WildcardType) {
                        it.type.set(boundType)
                    }
                }

                val extended = p.extendedByPatternNode?.let { sub -> extract(destructPattern(sub), ty) } ?: emptyList()
                listOf(sym) + extended
            }

            is SupportingPatternNode.DestructuredTuplePatternNode -> {
                // In this language, tuples are removed. Treat (pat) as grouping when size==1; () matches unit; otherwise error.
                when (p.tuple.size) {
                    0 -> {
                        if (ty != SemanticType.UnitType) {
                            throw CompileError("Unit pattern '()' can only bind unit type, got $ty").with(p)
                        }
                        emptyList()
                    }
                    1 -> extract(p.tuple[0], ty)
                    else -> throw CompileError("Tuple patterns are not supported in this spec").with(p)
                }
            }
        }
    }

    val symbols = extract(supportingPattern, semanticType)
    // Ensure no duplicate identifiers are produced by complex patterns like x @ (..)
    val seen = mutableSetOf<String>()
    symbols.forEach {
        if (!seen.add(it.identifier))
            throw CompileError("Duplicate binding for identifier '${it.identifier}' in pattern").with(pattern)
    }
    return symbols
}
