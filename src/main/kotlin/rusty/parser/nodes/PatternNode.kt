package rusty.parser.nodes

import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken

// corresponds to pattern without range
data class PatternNode(val patternNodes: List<SupportingPatternNode>, override val pointer: CompilerPointer): ASTNode(pointer) {
    companion object {
        val name get() = "Pattern"

        fun parse(ctx: Context): PatternNode {
            val pointer = ctx.peekPointer()
            val patternNodes: MutableList<SupportingPatternNode> = mutableListOf()
            putilsConsumeIfExistsToken(ctx, Token.O_OR)
            patternNodes.add(SupportingPatternNode.parse(ctx))
            while (ctx.peekToken() == Token.O_OR) { // separated via |
                ctx.stream.consume(1)
                patternNodes.add(SupportingPatternNode.parse(ctx))
            }
            return PatternNode(patternNodes, pointer)
        }
    }
}

sealed class SupportingPatternNode(pointer: CompilerPointer) : ASTNode(pointer) {
    companion object;

    data class LiteralPatternNode(val literalNode: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode, val isNegated: Boolean,
                                  override val pointer: CompilerPointer): SupportingPatternNode(pointer) {
        companion object {
            val name get() = "LiteralPattern"
        }
    }
    data class IdentifierPatternNode(val identifier: String, val isRef: Boolean, val isMut: Boolean, val extendedByPatternNode: PatternNode?,
                                     override val pointer: CompilerPointer): SupportingPatternNode(pointer) {
        companion object {
            val name get() = "IdentifierPattern"
        }
    }
    data class WildcardPatternNode(override val pointer: CompilerPointer): SupportingPatternNode(pointer) {
        companion object {
            val name get() = "WildcardPattern"
        }
    }

    data class DestructuredTuplePatternNode(val tuple: List<SupportingPatternNode>, override val pointer: CompilerPointer): SupportingPatternNode(pointer) {
        companion object {
            val name get() = "DestructuredTuplePattern"
        }
    }

    data class PathPatternNode(val path: PathInExpressionNode, override val pointer: CompilerPointer): SupportingPatternNode(pointer) {
        companion object {
            val name get() = "PathPattern"
        }
    }
}