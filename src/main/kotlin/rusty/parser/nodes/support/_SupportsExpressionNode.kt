package rusty.parser.nodes.support

import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.parse
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken
import rusty.parser.nodes.utils.Parsable
import rusty.parser.nodes.utils.Peekable

// TODO condition can also be a let chain
@Peekable @Parsable
data class ConditionsNode(val expression: ExpressionNode) {
    companion object {
        val name get() = "Conditions"

        fun parse(ctx: Context): ConditionsNode {
            ctx.callMe(name, enable_stack = false) {
                val expression = ExpressionNode.parse(ctx)
                return ConditionsNode(expression)
            }
        }
    }
}

@Peekable @Parsable
data class IfBranchNode(
    val condition: ConditionsNode,
    val then: ExpressionNode.WithBlockExpressionNode.BlockExpressionNode
)

@Peekable @Parsable
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

@Peekable @Parsable
data class MatchArmNode(val pattern: PatternNode, val guard: ExpressionNode?) {
    companion object {
        fun parse(ctx: Context): MatchArmNode {
            val pattern = PatternNode.parse(ctx)
            val guard = if (putilsConsumeIfExistsToken(ctx, Token.K_IF)) {
                ExpressionNode.parse(ctx)
            } else {
                null
            }
            return MatchArmNode(pattern, guard)
        }
    }
}

@Peekable @Parsable
data class MatchArmsNode(val arms: List<MatchArmNode>, val values: List<ExpressionNode>) {
    companion object {
        fun parse(ctx: Context): MatchArmsNode {
            putilsExpectToken(ctx, Token.O_LCURL)
            val arms = mutableListOf<MatchArmNode>()
            val expressions = mutableListOf<ExpressionNode>()
            val firstArm = MatchArmNode.parse(ctx)
            putilsExpectToken(ctx, Token.O_DOUBLE_ARROW)
            val firstExpr = ExpressionNode.parse(ctx)
            putilsConsumeIfExistsToken(ctx, Token.O_COMMA)
            arms.add(firstArm)
            expressions.add(firstExpr)
            while (ctx.peekToken() != Token.O_RCURL) {
                val arm = MatchArmNode.parse(ctx)
                putilsExpectToken(ctx, Token.O_DOUBLE_ARROW)
                val expr = ExpressionNode.parse(ctx)
                putilsConsumeIfExistsToken(ctx, Token.O_COMMA)
                arms.add(arm)
                expressions.add(expr)
            }
            putilsExpectToken(ctx, Token.O_RCURL)
            return MatchArmsNode(arms, expressions)
        }
    }
}