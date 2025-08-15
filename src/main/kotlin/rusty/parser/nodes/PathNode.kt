package rusty.parser.nodes

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken

data class PathIndentSegmentNode(val token: Token, val name: String?) {
    companion object {
        fun parse(ctx: Context): PathIndentSegmentNode {
            val nextToken = ctx.stream.read()
            return when (nextToken.token) {
                Token.I_IDENTIFIER -> PathIndentSegmentNode(Token.I_IDENTIFIER, nextToken.raw)
                Token.K_SELF -> PathIndentSegmentNode(Token.K_SELF, null)
                Token.K_TYPE_SELF -> PathIndentSegmentNode(Token.K_TYPE_SELF, null)
                else -> throw CompileError("When parsing PathIndentSegmentNode: Unexpected token ${nextToken.token}")
            }
        }
    }
}

data class PathInExpressionNode(val path: List<PathIndentSegmentNode>) {
    companion object {
        fun parse(ctx: Context): PathInExpressionNode {
            val path = mutableListOf<PathIndentSegmentNode>()
            path.add(PathIndentSegmentNode.parse(ctx))
            if (putilsConsumeIfExistsToken(ctx, Token.O_DOUBLE_COLON)) {
                path.add(PathIndentSegmentNode.parse(ctx))
            }
            return PathInExpressionNode(path)
        }
    }
}
