package rusty.parser.nodes.support

import rusty.lexer.Token
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.impl.parseTypeNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken
import rusty.parser.nodes.utils.Parsable
import rusty.parser.nodes.utils.Peekable

@Peekable @Parsable
data class GenericParamNode(val type: TypeNode)
@Peekable @Parsable
data class SelfParamNode(val isReference: Boolean, val isMutable: Boolean, val type: TypeNode?) {
    companion object
}

@Peekable @Parsable
sealed class FunctionParamNode {
    @Peekable @Parsable data class FunctionParamTypedPatternNode(val pattern: PatternNode, val type: TypeNode?) : FunctionParamNode()
    @Peekable @Parsable data class FunctionParamTypeNode(val type: TypeNode) : FunctionParamNode()
    @Peekable @Parsable data object FunctionParamWildcardNode : FunctionParamNode()
}

fun parseGenericParamNode(ctx: Context): GenericParamNode {
    return GenericParamNode(parseTypeNode(ctx))
}

fun SelfParamNode.Companion.parse(ctx: Context): SelfParamNode {
    val isReference = putilsConsumeIfExistsToken(ctx, Token.O_AND)
    val isMutable = putilsConsumeIfExistsToken(ctx, Token.K_MUT)
    var type: TypeNode? = null
    putilsExpectToken(ctx, Token.K_SELF)
    if (putilsConsumeIfExistsToken(ctx, Token.O_COLUMN)) {
        type = parseTypeNode(ctx)
    }
    return SelfParamNode(isReference, isMutable, type)
}

fun parseFunctionParamNode(ctx: Context): FunctionParamNode {
    if (ctx.peekToken() == Token.O_TRIPLE_DOT) {
        ctx.stream.consume(1)
        return FunctionParamNode.FunctionParamWildcardNode
    }
    return (
            ctx.tryParse("FunctionParam@Pattern") {
                val pattern = PatternNode.parse(ctx)
                putilsExpectToken(ctx, Token.O_COLUMN)
                var type: TypeNode? = null
                if (ctx.peekToken() != Token.O_TRIPLE_DOT) {
                    type = parseTypeNode(ctx)
                }
                FunctionParamNode.FunctionParamTypedPatternNode(pattern, type)
            } ?: FunctionParamNode.FunctionParamTypeNode(
                parseTypeNode(ctx)
            ))
}