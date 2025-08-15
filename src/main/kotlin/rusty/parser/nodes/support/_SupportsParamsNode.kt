package rusty.parser.nodes.support

import rusty.lexer.Token
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.TypeNode
import rusty.parser.nodes.impl.parseTypeNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectToken

data class GenericParamNode(val type: TypeNode)
data class SelfParamNode(val isReference: Boolean, val isMutable: Boolean, val type: TypeNode?) {
    companion object
}

sealed class FunctionParamNode {
    data class FunctionParamTypedPatternNode(val pattern: PatternNode, val type: TypeNode?) : FunctionParamNode()
    data class FunctionParamTypeNode(val type: TypeNode) : FunctionParamNode()
    data object FunctionParamWildcardNode : FunctionParamNode()
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
    return when (val rawType = ctx.tryParse("FunctionParam@Type") {
        parseTypeNode(ctx)
    }) {
        null -> {
            // regard as FunctionParamPattern → PatternNoTopAlt : ( Type | ... )
            val pattern = PatternNode.parse(ctx)
            putilsExpectToken(ctx, Token.O_COLUMN)
            var type: TypeNode? = null
            if (ctx.peekToken() != Token.O_TRIPLE_DOT) {
                type = parseTypeNode(ctx)
            }
            FunctionParamNode.FunctionParamTypedPatternNode(pattern, type)
        }
        else -> {
            // regard as Type
            FunctionParamNode.FunctionParamTypeNode(rawType)
        }
    }
}