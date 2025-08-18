package rusty.parser.nodes.support

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.lexer.TokenBearer
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.impl.parseTypeNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsExpectListWithin

data class TypePathSegment(
    val pathIndentSegment: TokenBearer,
    val generics: GenericArgsNode?
) {
    companion object {
        fun parse(ctx: Context): TypePathSegment {
            val pathIndentSegment = ctx.stream.read()
            if (!setOf(Token.I_IDENTIFIER, Token.K_SUPER, Token.K_SELF, Token.K_TYPE_SELF, Token.K_CRATE)
                .contains(pathIndentSegment.token)) {
                throw CompileError("Invalid type PathIdentSegment: $pathIndentSegment at ${pathIndentSegment.lineNumber}:${pathIndentSegment.columnNumber}")
            }
            val cur = ctx.stream.cur
            val nToken = ctx.stream.peekAtOrNull(cur + 1)?.token
            val nnToken = ctx.stream.peekAtOrNull(cur + 2)?.token
            var generics: GenericArgsNode? = null
            if (nToken == Token.O_DOUBLE_COLON && nnToken == Token.O_LANG) {
                // PathIdentSegment :: GenericArgs
                ctx.stream.consume(1)
                generics = GenericArgsNode.parse(ctx)
            }
            return TypePathSegment(pathIndentSegment, generics)
        }
    }
}

// After simplifying, each generic arg must be a TypeNode
data class GenericArgsNode(val args: List<TypeNode>) {
    companion object {
        fun parse(ctx: Context): GenericArgsNode {
            val args = putilsExpectListWithin(ctx,
                ::parseTypeNode,
                Pair(Token.O_LANG, Token.O_RANG))
            return GenericArgsNode(args)
        }
    }
}