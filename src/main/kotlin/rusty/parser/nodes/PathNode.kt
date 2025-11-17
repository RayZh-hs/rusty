package rusty.parser.nodes

import rusty.core.CompileError
import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.putils.ParsingContext
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.nodes.utils.Parsable

@Parsable
data class PathIndentSegmentNode(val token: Token, val name: String?, override val pointer: CompilerPointer): ASTNode(pointer) {
    companion object {
        val name: String = "PathIndentSegment"

        fun parse(ctx: ParsingContext): PathIndentSegmentNode {
            val cur = ctx.peekPointer()
            val nextToken = ctx.stream.read()
            return when (nextToken.token) {
                Token.I_IDENTIFIER -> PathIndentSegmentNode(Token.I_IDENTIFIER, nextToken.raw, cur)
                Token.K_SELF -> PathIndentSegmentNode(Token.K_SELF, null, cur)
                Token.K_TYPE_SELF -> PathIndentSegmentNode(Token.K_TYPE_SELF, null, cur)
                else -> throw CompileError("When parsing PathIndentSegmentNode: Unexpected token ${nextToken.token}")
            }
        }
    }
}

@Parsable
data class PathInExpressionNode(val path: List<PathIndentSegmentNode>, override val pointer: CompilerPointer) : ASTNode(pointer) {
    companion object {
        val name: String = "PathInExpression"

        fun parse(ctx: ParsingContext): PathInExpressionNode {
            val cur = ctx.peekPointer()
            val path = mutableListOf<PathIndentSegmentNode>()
            path.add(PathIndentSegmentNode.parse(ctx))
            if (putilsConsumeIfExistsToken(ctx, Token.O_DOUBLE_COLON)) {
                path.add(PathIndentSegmentNode.parse(ctx))
            }
            return PathInExpressionNode(path, cur)
        }
    }
}
