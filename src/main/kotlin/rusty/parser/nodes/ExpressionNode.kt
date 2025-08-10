package rusty.parser.nodes

import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.impl.peek
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

// Expression nodes use a Pratt-based parsing system
sealed class ExpressionNode {
    companion object;

    sealed class WithBlockExpressionNode : ExpressionNode() {
        companion object;

        data class BlockExpressionNode(val statements: List<StatementNode>) : WithBlockExpressionNode() {
            companion object;
        }

        data class ConstBlockExpressionNode(val expression: BlockExpressionNode) : WithBlockExpressionNode() {
            companion object;
        }

        data class LoopBlockExpressionNode(val expression: BlockExpressionNode) : WithBlockExpressionNode() {
            companion object;
        }

        data class WhileBlockExpressionNode(val condition: ConditionsNode, val expression: BlockExpressionNode) :
            WithBlockExpressionNode() {
            companion object;
        }

        data class IfBlockExpressionNode(val ifs: List<IfBranchNode>, val elseBranch: BlockExpressionNode?) :
            WithBlockExpressionNode() {
            companion object;
        }
    }

    sealed class WithoutBlockExpressionNode : ExpressionNode() {
        companion object;

        sealed class LiteralExpressionNode : WithoutBlockExpressionNode() {
            companion object;

            data class I32LiteralNode(val value: Int) : LiteralExpressionNode()

            data class U32LiteralNode(val value: UInt) : LiteralExpressionNode()

            data class StringLiteralNode(val value: String) : LiteralExpressionNode()

            data class CharLiteralNode(val value: Char) : LiteralExpressionNode()
        }

        data class PathExpressionNode(val path: String) : WithoutBlockExpressionNode() {
            companion object
        }

        sealed class OperatorExpressionNode : WithoutBlockExpressionNode() {
            data class InfixOperatorNode(val left: ExpressionNode, val op: Token, val right: ExpressionNode) : OperatorExpressionNode()
            data class PrefixOperatorNode(val op: Token, val expr: ExpressionNode) : OperatorExpressionNode()
        }

        // A primitive can be either an array or
        sealed class PrimitiveBasedExpressionNode

        data class IndexExpressionNode(val array: WithoutBlockExpressionNode, val index: WithoutBlockExpressionNode) : WithoutBlockExpressionNode()

        data class TupleIndexingExpressionNode(val tuple: WithoutBlockExpressionNode, val index: Int) : WithoutBlockExpressionNode()

        data class CallExpressionNode(val callee: WithoutBlockExpressionNode, val args: List<WithoutBlockExpressionNode>) : WithoutBlockExpressionNode()

        data class MethodCallExpressionNode(val receiver: WithoutBlockExpressionNode, val method: String, val args: List<WithoutBlockExpressionNode>) : WithoutBlockExpressionNode()

        data class FieldExpressionNode(val receiver: WithoutBlockExpressionNode, val field: String) : WithoutBlockExpressionNode()

        data class ReturnExpressionNode(val expr: WithoutBlockExpressionNode?) : WithoutBlockExpressionNode()

        data class BreakExpressionNode(val expr: WithoutBlockExpressionNode?) : WithoutBlockExpressionNode()

        data object ContinueExpressionNode : WithoutBlockExpressionNode()

        data object UnderscoreExpressionNode : WithoutBlockExpressionNode()
    }
}

fun ExpressionNode.Companion.parse(ctx: Context): ExpressionNode {
    return if (ExpressionNode.WithBlockExpressionNode.peek(ctx)) {
        ExpressionNode.WithBlockExpressionNode.parse(ctx)
    } else {
        ExpressionNode.WithoutBlockExpressionNode.parse(ctx)
    }
}
