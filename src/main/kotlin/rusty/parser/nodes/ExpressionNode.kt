package rusty.parser.nodes

import rusty.lexer.Token
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.impl.peek
import rusty.parser.nodes.support.ConditionsNode
import rusty.parser.nodes.support.IfBranchNode
import rusty.parser.nodes.support.PrimitiveExpressionUnit
import rusty.parser.nodes.utils.Peekable
import rusty.parser.putils.Context

// Expression nodes use a Pratt-based parsing system
sealed class ExpressionNode {
    companion object;

    @Peekable
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

        data class PrimitiveExpressionNode(val units: List<PrimitiveExpressionUnit>) : WithoutBlockExpressionNode() {
            companion object;
        }

        sealed class ControlFlowExpressionNode : WithoutBlockExpressionNode() {
            data class ReturnExpressionNode(val expr: ExpressionNode?) : ControlFlowExpressionNode()
            data class BreakExpressionNode(val expr: ExpressionNode?) : ControlFlowExpressionNode()
            data object ContinueExpressionNode : ControlFlowExpressionNode()
        }

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
