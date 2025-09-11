package rusty.semantic.visitors.companions

import com.andreapivetta.kolor.cyan
import com.andreapivetta.kolor.darkGray
import com.andreapivetta.kolor.yellow
import rusty.core.CompileError
import rusty.parser.nodes.utils.afterWhich
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
        println("Entering scope: ".cyan() + currentScope.toString().darkGray())
        curStack.push(0)
    }

    private fun exitScope() {
        println("Exiting scope: ".yellow() + currentScope.toString().darkGray())
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