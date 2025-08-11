package rusty.parser.nodes.support

import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode
import rusty.parser.nodes.parse
import rusty.parser.putils.Context

// TODO condition can also be a let chain
data class ConditionsNode(val expression: ExpressionNode) {
    companion object {
        fun parse(ctx: Context): ConditionsNode {
            val expression = ExpressionNode.parse(ctx)
            return ConditionsNode(expression)
        }
    }
}

data class IfBranchNode(
    val condition: ConditionsNode,
    val then: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode
)

sealed class PrimitiveExpressionUnit {
    data class PrimitiveIdentifier(val identifier: String) : PrimitiveExpressionUnit()
    data class PrimitiveAccessor(val token: Token) : PrimitiveExpressionUnit()
    data class PrimitiveCall(val args: List<ExpressionNode>) : PrimitiveExpressionUnit()
    data class PrimitiveIndexing(val index: ExpressionNode) : PrimitiveExpressionUnit()
}