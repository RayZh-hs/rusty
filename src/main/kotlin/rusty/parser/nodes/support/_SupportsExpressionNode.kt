package rusty.parser.nodes.support

import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.parse
import rusty.parser.nodes.parseWithoutStruct
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken

// TODO condition can also be a let chain
data class ConditionsNode(val expression: ExpressionNode) {
    companion object {
        fun parse(ctx: Context): ConditionsNode {
            val expression = ExpressionNode.parseWithoutStruct(ctx)
            return ConditionsNode(expression)
        }
    }
}

data class IfBranchNode(
    val condition: ConditionsNode,
    val then: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode
)

data class StructExprFieldNode(
    val identifier: String,
    val expressionNode: ExpressionNode?
) {
    companion object {
        fun parse(ctx: Context): StructExprFieldNode {
            val identifier = putilsExpectToken(ctx, Token.I_IDENTIFIER)
            val expressionNode = if (putilsConsumeIfExistsToken(ctx, Token.O_COLUMN)) {
                ExpressionNode.parse(ctx)
            } else {
                null
            }
            return StructExprFieldNode(identifier, expressionNode)
        }
    }
}