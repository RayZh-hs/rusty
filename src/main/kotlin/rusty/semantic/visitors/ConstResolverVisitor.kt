package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.companions.ConstValueResolverCompanion

class ConstResolverVisitor(ctx: Context) : ScopeAwareVisitorBase(ctx) {
    private val constResolver: ConstValueResolverCompanion = ConstValueResolverCompanion(ctx)

    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        TODO()
    }

    override fun visitConstBlockExpression(node: ExpressionNode.WithBlockExpressionNode.ConstBlockExpressionNode) {
        throw CompileError("Const blocks are not supported").with(node)
    }
}