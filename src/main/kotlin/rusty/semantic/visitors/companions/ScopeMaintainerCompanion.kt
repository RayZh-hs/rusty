package rusty.semantic.visitors.companions

import rusty.core.CompileError
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import java.util.Stack

class ScopeMaintainerCompanion(ctx: Context) {
    var currentScope: Scope = ctx.scopeTree
    private val curStack: Stack<Int> = Stack<Int>().apply { push(0) }

    private fun enterScope() {
        if (curStack.peek() >= currentScope.children.size) {
            throw CompileError("No more child scopes available for $currentScope")
        }
        currentScope = currentScope.children[curStack.peek()]
        curStack.push(0)
    }

    private fun exitScope() {
        curStack.pop()
        if (curStack.isNotEmpty()) {
            curStack.push(curStack.pop() + 1)
        }
        currentScope = currentScope.parent!!
    }

    fun withNextScope(block: () -> Unit) {
        enterScope()
        block()
        exitScope()
    }
}