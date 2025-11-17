package rusty.semantic.visitors.bases

import rusty.parser.nodes.CrateNode
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.support.FunctionParamNode
import rusty.parser.nodes.support.SelfParamNode
import rusty.semantic.support.SemanticContext
import rusty.semantic.visitors.companions.ScopeMaintainerCompanion

open class ScopeAwareVisitorBase(ctx: SemanticContext) : SimpleVisitorBase(ctx) {
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

    override fun visitTraitItem(node: ItemNode.TraitItemNode) {
        scopeMaintainer.withNextScope {
            super.visitTraitItem(node)
        }
    }

    fun visitTraitItemInternal(node: ItemNode.TraitItemNode) {
        super.visitTraitItem(node)
    }
    
    // New visitor methods for SelfParamNode and FunctionParamNode
    // These don't typically need special scope handling, so we delegate to parent
    override fun visitSelfParam(node: SelfParamNode) {
        super.visitSelfParam(node)
    }
    
    override fun visitFunctionParamTypedPattern(node: FunctionParamNode.FunctionParamTypedPatternNode) {
        super.visitFunctionParamTypedPattern(node)
    }
    
    override fun visitFunctionParamType(node: FunctionParamNode.FunctionParamTypeNode) {
        super.visitFunctionParamType(node)
    }
    
    override fun visitFunctionParamWildcard(node: FunctionParamNode.FunctionParamWildcardNode) {
        super.visitFunctionParamWildcard(node)
    }
}