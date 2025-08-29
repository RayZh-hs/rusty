package rusty.semantic.support

import rusty.parser.ASTTree

// This file defines the global context object used in Semantic Check

data class Context (
    val astTree: ASTTree,
    val scopeTree: Scope = Scope.ofPrelude()
) {
    fun toString(withColor: Boolean): String {
        return "Context(astTree=$astTree, scopeTree=$scopeTree)"
    }
}