package rusty.semantic.visitors.companions

import rusty.core.CompileError
import rusty.parser.nodes.utils.afterWhich
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.Scope
import java.util.Stack

class ScopeMaintainerCompanion(ctx: SemanticContext) {
    var currentScope: Scope = ctx.scopeTree
    private val curStack: Stack<Int> = Stack<Int>().apply { push(0) }

    private fun enterScope() {
        if (curStack.peek() >= currentScope.children.size) {
            throw CompileError("No more child scopes available for $currentScope")
        }
        currentScope = currentScope.children[curStack.peek()]
//        println("Entering scope: ".cyan() + currentScope.toString().darkGray())
        curStack.push(0)
    }

    private fun exitScope() {
//        println("Exiting scope: ".yellow() + currentScope.toString().darkGray())
        curStack.pop()
        if (curStack.isNotEmpty()) {
            curStack.push(curStack.pop() + 1)
        }
        currentScope = currentScope.parent!!
    }

    fun <R> withNextScope(block: (Scope) -> R): R {
        enterScope()
        return block(currentScope).afterWhich {
            exitScope()
        }
    }

    fun skipScope() {
        if (curStack.isNotEmpty()) {
            curStack.push(curStack.pop() + 1)
        }
    }
}