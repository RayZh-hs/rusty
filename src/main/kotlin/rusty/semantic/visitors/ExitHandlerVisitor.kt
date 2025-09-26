package rusty.semantic.visitors

import com.andreapivetta.kolor.yellow
import rusty.core.CompileError
import rusty.core.CompilerPointer
import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.StatementNode
import rusty.semantic.support.Context
import rusty.semantic.visitors.bases.SimpleVisitorBase
import rusty.settings.Settings

class ExitHandlerVisitor(ctx: Context): SimpleVisitorBase(ctx) {

    val exitPointerCollector = mutableListOf<CompilerPointer>()

    override fun visitCrate(node: CrateNode) {
        // must not override exit()
        node.items.forEach { item ->
            if (item is ItemNode.FunctionItemNode && item.identifier == "exit")
                throw CompileError("Cannot override exit() function in crate").with(item).at(item.pointer)
        }

        super.visitCrate(node)

        // ensure that the main() function calls exit() as the final action
        val mainFunction = node.items.filterIsInstance<ItemNode.FunctionItemNode>().find { it.identifier == "main" }
        if (mainFunction != null) {
            if (!Settings.MANDATORY_EXIT_IN_MAIN)   return
            val expr = mainFunction.withBlockExpressionNode as ExpressionNode.WithBlockExpressionNode.BlockExpressionNode
            if (expr.trailingExpression != null) {
                if (!expr.trailingExpression.isExitCall()) {
                    throw CompileError("main() function must call exit() as the final action")
                        .with(expr.trailingExpression).at(expr.trailingExpression.pointer)
                }
            }
            else {
                val lastExpressionStatement = expr.statements.findLast { it is StatementNode.ExpressionStatementNode }
                if (lastExpressionStatement == null)
                    throw CompileError("main() function cannot be empty")
                        .with(mainFunction).at(mainFunction.pointer)
                else {
                    val lastExpression = (lastExpressionStatement as StatementNode.ExpressionStatementNode).expression
                    if (!lastExpression.isExitCall()) {
                        throw CompileError("main() function must call exit() as the final action")
                            .with(lastExpression).at(lastExpression.pointer)
                    }
                }
            }
        } else if (Settings.MANDATORY_MAIN_FUNCTION) {
            throw CompileError("Crate must have a main() function").with(node).at(node.pointer)
        }

        if (Settings.BANISH_EXIT_FROM_NON_MAIN) {
            val supposedCount = mainFunction?.let { 1 } ?: 0
            if (exitPointerCollector.size > supposedCount) {
                var err =  CompileError("exit() function can only be called inside main() function")
                exitPointerCollector.forEach { ptr ->
                    err = err.with(this.ctx).at(ptr)
                }
                throw err
            }
        }
    }

    override fun visitCallExpression(node: ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode) {
        if (node.isExitCall()) {
            println("Resolved exit() at: " + "${node.pointer}".yellow())
            exitPointerCollector.add(node.pointer)
        }
    }

    private fun ExpressionNode.isExitCall(): Boolean {
        if (this is ExpressionNode.WithoutBlockExpressionNode.CallExpressionNode) {
            if (this.callee is ExpressionNode.WithoutBlockExpressionNode.PathExpressionNode) {
                val pathInExpression = this.callee.pathInExpressionNode
                if (pathInExpression.path.size == 1 && pathInExpression.path[0].name == "exit") {
                    return true
                }
            }
        }
        return false
    }
}