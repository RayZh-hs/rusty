package rusty.parser.nodes

import rusty.lexer.Token
import rusty.parser.nodes.impl.parseTypeNode
import rusty.parser.nodes.support.FunctionParamNode
import rusty.parser.nodes.support.GenericParamNode
import rusty.parser.nodes.support.SelfParamNode
import rusty.parser.nodes.support.parse
import rusty.parser.nodes.support.parseFunctionParamNode
import rusty.parser.nodes.support.parseGenericParamNode
import rusty.parser.putils.Context
import rusty.parser.putils.putilsExpectListWithin
import rusty.parser.putils.putilsExpectToken

sealed class ParamsNode {
    data class GenericParamsNode(val genericParams: List<GenericParamNode>) {
        companion object
    }

    data class FunctionParamsNode(val selfParam: SelfParamNode?, val functionParams: List<FunctionParamNode>) {
        companion object
    }
}

fun ParamsNode.GenericParamsNode.Companion.parse(ctx: Context): ParamsNode.GenericParamsNode {
    val types = putilsExpectListWithin(ctx, ::parseGenericParamNode, Pair(Token.O_LANG, Token.O_RANG))
    return ParamsNode.GenericParamsNode(types)
}

fun ParamsNode.FunctionParamsNode.Companion.parse(ctx: Context): ParamsNode.FunctionParamsNode {
    putilsExpectToken(ctx, Token.O_LPAREN)
    val selfParam = ctx.tryParse("FunctionParams@SelfParam") {
        SelfParamNode.parse(ctx)
    }
    val args = putilsExpectListWithin(ctx, ::parseFunctionParamNode, Pair(null, Token.O_RPAREN))
    return ParamsNode.FunctionParamsNode(selfParam, args)
}