package rusty.semantic.support

import rusty.parser.ASTTree

// This file defines the global context object used in Semantic Check

data class Context (
    val astTree: ASTTree,
    val scopeTree: Scope = Scope.from(parent = null, name = "prelude", pointer = null)
) {
    fun toString(withColor: Boolean): String {
        return "Context(astTree=$astTree, scopeTree=$scopeTree)"
    }
}