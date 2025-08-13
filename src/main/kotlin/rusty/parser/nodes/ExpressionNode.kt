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

        data class BlockExpressionNode(val statements: List<StatementNode>, val trailingExpression: ExpressionNode?) : WithBlockExpressionNode() {
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

        // Literal Expression Node
        sealed class LiteralExpressionNode : WithoutBlockExpressionNode() {
            companion object;

            data class I32LiteralNode(val value: Int) : LiteralExpressionNode()
            data class U32LiteralNode(val value: UInt) : LiteralExpressionNode()
            data class StringLiteralNode(val value: String) : LiteralExpressionNode()
            data class CharLiteralNode(val value: Char) : LiteralExpressionNode()
            data class BoolLiteralNode(val value: Boolean) : LiteralExpressionNode()
        }

        // Literal-like Expression Node
        data object UnderscoreLiteralNode : LiteralExpressionNode()
        data class TupleExpressionNode(val elements: List<ExpressionNode>) : WithoutBlockExpressionNode()
        data class ArrayExpressionNode(val elements: List<ExpressionNode>) : WithoutBlockExpressionNode()

        // Literal Modification Node
        // - handles (args)
        data class CallExpressionNode(val callee: ExpressionNode, val arguments: List<ExpressionNode>) : WithoutBlockExpressionNode()
        // - handles [arg]
        data class IndexExpressionNode(val base: ExpressionNode, val index: ExpressionNode) : WithoutBlockExpressionNode()
        // - handles [base].[field]
        data class FieldExpressionNode(val base: ExpressionNode, val field: String) : WithoutBlockExpressionNode()
        // - handles [id0]::[id1]::[id2]. !IMP: A single identifier is also considered a PathExpressionNode (where path.size == 1)
        data class PathExpressionNode(val path: List<String>) : WithoutBlockExpressionNode()
        // - handles [tuple].[id] where id is an integer
        // - note: I don't know why rust had chosen a.0 as its tuple indexing grammar. The good thing is 0 cannot be an expression, so lookaheads work
        data class TupleIndexingNode(val base: ExpressionNode, val index: Int) : WithoutBlockExpressionNode()

        // Generic Expression Node
        data class InfixOperatorNode(val left: ExpressionNode, val op: Token, val right: ExpressionNode) : WithoutBlockExpressionNode()
        data class PrefixOperatorNode(val op: Token, val expr: ExpressionNode) : WithoutBlockExpressionNode()

        // Control Flow Expression Node
        sealed class ControlFlowExpressionNode : WithoutBlockExpressionNode() {
            data class ReturnExpressionNode(val expr: ExpressionNode?) : ControlFlowExpressionNode()
            data class BreakExpressionNode(val expr: ExpressionNode?) : ControlFlowExpressionNode()
            data object ContinueExpressionNode : ControlFlowExpressionNode()
        }
    }
}

fun ExpressionNode.Companion.parse(ctx: Context): ExpressionNode {
    return if (ExpressionNode.WithBlockExpressionNode.peek(ctx)) {
        ExpressionNode.WithBlockExpressionNode.parse(ctx)
    } else {
        ExpressionNode.WithoutBlockExpressionNode.parse(ctx)
    }
}
