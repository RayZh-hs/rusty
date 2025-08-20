package rusty.parser.nodes

import rusty.core.CompilerPointer
import rusty.lexer.Token
import rusty.parser.nodes.support.FunctionParamNode
import rusty.parser.nodes.support.GenericParamNode
import rusty.parser.nodes.support.SelfParamNode
import rusty.parser.nodes.support.parse
import rusty.parser.nodes.support.parseFunctionParamNode
import rusty.parser.nodes.support.parseGenericParamNode
import rusty.parser.nodes.utils.Parsable
import rusty.parser.nodes.utils.Peekable
import rusty.parser.nodes.utils.afterWhich
import rusty.parser.putils.Context
import rusty.parser.putils.putilsConsumeIfExistsToken
import rusty.parser.putils.putilsExpectListWithin
import rusty.parser.putils.putilsExpectToken

@Parsable
sealed class ParamsNode(pointer: CompilerPointer): ASTNode(pointer) {
    @Parsable
    data class GenericParamsNode(val genericParams: List<GenericParamNode>, override val pointer: CompilerPointer): ParamsNode(pointer) {
        companion object {
            val name: String = "GenericParamsNode"
        }
    }

    @Parsable
    data class FunctionParamsNode(val selfParam: SelfParamNode?, val functionParams: List<FunctionParamNode>, override val pointer: CompilerPointer): ParamsNode(pointer) {
        companion object {
            val name: String = "FunctionParamsNode"
        }
    }
}

fun ParamsNode.GenericParamsNode.Companion.parse(ctx: Context): ParamsNode.GenericParamsNode {
    ctx.callMe(name) {
        val types = putilsExpectListWithin(ctx, ::parseGenericParamNode, Pair(Token.O_LANG, Token.O_RANG))
        return ParamsNode.GenericParamsNode(types, ctx.topPointer())
    }
}

fun ParamsNode.FunctionParamsNode.Companion.parse(ctx: Context): ParamsNode.FunctionParamsNode {
    ctx.callMe(name) {
        putilsExpectToken(ctx, Token.O_LPAREN)
        val selfParam = ctx.tryParse("FunctionParams@SelfParam") {
            SelfParamNode.parse(ctx).afterWhich {
                putilsConsumeIfExistsToken(ctx, Token.O_COMMA)
            }
        }
        val args = putilsExpectListWithin(ctx, ::parseFunctionParamNode, Pair(null, Token.O_RPAREN))
        return ParamsNode.FunctionParamsNode(selfParam, args, ctx.topPointer())
    }
}