package rusty.semantic.support

import rusty.core.MemoryBank
import rusty.parser.ASTTree
import rusty.parser.nodes.ExpressionNode

// This file defines the global context object used in Semantic Check
// Added: expressionTypeMemory for caching resolved expression types

data class SemanticContext (
    val astTree: ASTTree,
    val scopeTree: Scope = Scope.ofPrelude(),

    // Memory for caching resolved expression types to avoid repeated computation
    val expressionTypeMemory: MemoryBank<ExpressionNode, SemanticType> = MemoryBank(),

    // Memory for caching the number of dereference times during auto-deref
    val derefCountMemory: MemoryBank<SemanticType, Int> = MemoryBank(),
) {
    fun toString(withColor: Boolean): String {
        return "Context(astTree=$astTree, scopeTree=$scopeTree, expressionTypeMemory=$expressionTypeMemory, derefCountMemory=$derefCountMemory)"
    }
}