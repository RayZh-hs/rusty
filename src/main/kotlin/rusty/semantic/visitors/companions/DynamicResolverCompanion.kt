package rusty.semantic.visitors.companions

import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.TypeNode
import rusty.semantic.support.Context
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticValue

class DynamicResolverCompanion(ctx: Context, private val selfResolverRef: SelfResolverCompanion) {
    val src = StaticResolverCompanion(ctx, selfResolverRef)

    fun resolveConstExpression(node: ExpressionNode, scope: Scope): SemanticValue = src.resolveConstExpression(node, scope)
    fun resolveTypeNode(node: TypeNode, scope: Scope): SemanticType = src.resolveTypeNode(node, scope)

    fun resolveLeftValueExpression(node: ExpressionNode, scope: Scope): SemanticType {
        TODO()
    }

    fun resolveRightValueExpression(node: ExpressionNode, scope: Scope): SemanticValue {
        TODO()
    }
}