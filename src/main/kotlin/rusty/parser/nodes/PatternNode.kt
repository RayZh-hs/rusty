package rusty.parser.nodes

import rusty.lexer.Token
import rusty.parser.nodes.impl.parse
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken

// corresponds to pattern without range
data class PatternNode(val patternNodes: List<SupportingPatternNode>) {
    companion object {
        fun parse(ctx: Context): PatternNode {
            val patternNodes: MutableList<SupportingPatternNode> = mutableListOf()
            putilsConsumeIfExistsToken(ctx, Token.O_OR)
            patternNodes.add(SupportingPatternNode.parse(ctx))
            while (ctx.peekToken() == Token.O_OR) { // separated via |
                ctx.stream.consume(1)
                patternNodes.add(SupportingPatternNode.parse(ctx))
            }
            return PatternNode(patternNodes)
        }
    }
}

sealed class SupportingPatternNode {
    companion object;

    data class LiteralPatternNode(val literalNode: ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode, val isNegated: Boolean): SupportingPatternNode() {
        companion object
    }
    data class IdentifierPatternNode(val identifier: String, val isRef: Boolean, val isMut: Boolean, val extendedByPatternNode: PatternNode?): SupportingPatternNode() {
        companion object
    }
}