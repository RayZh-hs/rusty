package rusty.parser.nodes

import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.nodes.impl.peek
import rusty.parser.nodes.support.ConditionsNode
import rusty.parser.nodes.support.IfBranchNode
import rusty.parser.nodes.support.MatchArmsNode
import rusty.parser.nodes.support.StructExprFieldNode
import rusty.parser.nodes.utils.Parsable
import rusty.parser.nodes.utils.Peekable
import rusty.parser.putils.Context

// Expression nodes use a Pratt-based parsing system
sealed class ExpressionNode(pointer: CompilerPointer) : ASTNode(pointer) {
    companion object;

    @Peekable
    @Parsable
    sealed class WithBlockExpressionNode(pointer: CompilerPointer) : ExpressionNode(pointer) {
        companion object;

        @Peekable
        @Parsable
        data class BlockExpressionNode(
            val statements: List<StatementNode>, val trailingExpression: ExpressionNode?, override val pointer: CompilerPointer
        ) : WithBlockExpressionNode(pointer) {
            companion object;
        }

        data class ConstBlockExpressionNode(val expression: BlockExpressionNode, override val pointer: CompilerPointer) :
            WithBlockExpressionNode(pointer) {
            companion object;
        }

        data class LoopBlockExpressionNode(val expression: BlockExpressionNode, override val pointer: CompilerPointer) :
            WithBlockExpressionNode(pointer) {
            companion object;
        }

        data class WhileBlockExpressionNode(
            val condition: ConditionsNode,
            val expression: BlockExpressionNode,
            override val pointer: CompilerPointer
        ) :
            WithBlockExpressionNode(pointer) {
            companion object;
        }

        data class IfBlockExpressionNode(
            val ifs: List<IfBranchNode>,
            val elseBranch: BlockExpressionNode?,
            override val pointer: CompilerPointer
        ) :
            WithBlockExpressionNode(pointer) {
            companion object;
        }

        data class MatchBlockExpressionNode(
            val scrutinee: ExpressionNode,
            val matchArmsNode: MatchArmsNode,
            override val pointer: CompilerPointer
        ) :
            WithBlockExpressionNode(pointer) {
            companion object;
        }
    }

    @Parsable
    sealed class WithoutBlockExpressionNode(pointer: CompilerPointer) : ExpressionNode(pointer) {
        companion object;

        // Literal Expression Node
        sealed class LiteralExpressionNode(pointer: CompilerPointer) : WithoutBlockExpressionNode(pointer) {
            companion object;

            data class I32LiteralNode(val value: Int, override val pointer: CompilerPointer) : LiteralExpressionNode(pointer)
            data class U32LiteralNode(val value: UInt, override val pointer: CompilerPointer) : LiteralExpressionNode(pointer)
            data class StringLiteralNode(val value: String, override val pointer: CompilerPointer) : LiteralExpressionNode(pointer)
            data class CharLiteralNode(val value: Char, override val pointer: CompilerPointer) : LiteralExpressionNode(pointer)
            data class BoolLiteralNode(val value: Boolean, override val pointer: CompilerPointer) : LiteralExpressionNode(pointer)
        }

        // Literal-like Expression Node
        data class UnderscoreExpressionNode(override val pointer: CompilerPointer) : WithoutBlockExpressionNode(pointer)
        data class TupleExpressionNode(val elements: List<ExpressionNode>, override val pointer: CompilerPointer) :
            WithoutBlockExpressionNode(pointer)

        data class ArrayExpressionNode(
            val elements: List<ExpressionNode>,
            val repeat: ExpressionNode,
            override val pointer: CompilerPointer
        ) : WithoutBlockExpressionNode(pointer)

        data class StructExpressionNode(
            val pathInExpressionNode: PathInExpressionNode,
            val fields: List<StructExprFieldNode>,
            override val pointer: CompilerPointer
        ) : WithoutBlockExpressionNode(pointer)

        // Literal Modification Node
        // - handles (args)
        data class CallExpressionNode(
            val callee: ExpressionNode,
            val arguments: List<ExpressionNode>,
            override val pointer: CompilerPointer
        ) : WithoutBlockExpressionNode(pointer)

        // - handles [arg]
        data class IndexExpressionNode(val base: ExpressionNode, val index: ExpressionNode, override val pointer: CompilerPointer) :
            WithoutBlockExpressionNode(pointer)

        // - handles [base].[field]
        data class FieldExpressionNode(val base: ExpressionNode, val field: String, override val pointer: CompilerPointer) :
            WithoutBlockExpressionNode(pointer)

        // - handles [id0]::[id1]::[id2]. !IMP: A single identifier is also considered a PathExpressionNode (where path.size == 1)
        data class PathExpressionNode(val pathInExpressionNode: PathInExpressionNode, override val pointer: CompilerPointer) :
            WithoutBlockExpressionNode(pointer)

        // - handles [tuple].[id] where id is an integer
        // - note: I don't know why rust had chosen a.0 as its tuple indexing grammar. The good thing is 0 cannot be an expression, so lookaheads work
        data class TupleIndexingNode(val base: ExpressionNode, val index: Int, override val pointer: CompilerPointer) :
            WithoutBlockExpressionNode(pointer)

        // Generic Expression Node
        data class InfixOperatorNode(
            val left: ExpressionNode,
            val op: Token,
            val right: ExpressionNode,
            override val pointer: CompilerPointer
        ) : WithoutBlockExpressionNode(pointer)

        data class PrefixOperatorNode(val op: Token, val expr: ExpressionNode, override val pointer: CompilerPointer) :
            WithoutBlockExpressionNode(pointer)

        // Control Flow Expression Node
        sealed class ControlFlowExpressionNode(pointer: CompilerPointer) : WithoutBlockExpressionNode(pointer) {
            data class ReturnExpressionNode(val expr: ExpressionNode?, override val pointer: CompilerPointer) :
                ControlFlowExpressionNode(pointer)

            data class BreakExpressionNode(val expr: ExpressionNode?, override val pointer: CompilerPointer) :
                ControlFlowExpressionNode(pointer)

            data class ContinueExpressionNode(override val pointer: CompilerPointer) : ControlFlowExpressionNode(pointer)
        }
    }
}

fun ExpressionNode.Companion.peekIsBlock(ctx: Context): Boolean {
    return ExpressionNode.WithBlockExpressionNode.peek(ctx)
}

fun ExpressionNode.Companion.parse(ctx: Context): ExpressionNode {
    return if (ExpressionNode.WithBlockExpressionNode.peek(ctx)) {
        ExpressionNode.WithBlockExpressionNode.parse(ctx)
    } else {
        ExpressionNode.WithoutBlockExpressionNode.parse(ctx)
    }
}
