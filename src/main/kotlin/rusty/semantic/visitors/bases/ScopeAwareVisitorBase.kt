package rusty.semantic.visitors.bases

import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.visitors.companions.ScopeMaintainerCompanion

open class ScopeAwareVisitorBase(ctx: Context) : SimpleVisitorBase(ctx) {
    protected val scopeMaintainer = ScopeMaintainerCompanion(ctx)

    fun currentScope() = scopeMaintainer.currentScope

    override fun run() {
        visit(ctx.astTree)
    }

    override fun visitCrate(node: CrateNode) {
        scopeMaintainer.withNextScope {
            super.visitCrate(node)
        }
    }

    override fun visitBlockExpression(node: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode) {
        scopeMaintainer.withNextScope {
            super.visitBlockExpression(node)
        }
    }

    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        scopeMaintainer.withNextScope {
            super.visitFunctionItem(node)
        }
    }

    protected fun visitFunctionInternal(node: ItemNode.FunctionItemNode) {
        super.visitFunctionItem(node)
    }

    override fun visitInherentImplItem(node: ItemNode.ImplItemNode.InherentImplItemNode) {
        scopeMaintainer.withNextScope {
            super.visitInherentImplItem(node)
        }
    }

    override fun visitTraitImplItem(node: ItemNode.ImplItemNode.TraitImplItemNode) {
        scopeMaintainer.withNextScope {
            super.visitTraitImplItem(node)
        }
    }
}