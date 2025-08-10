package rusty.parser.nodes

import rusty.parser.putils.Context

sealed class ParamsNode {
    data class GenericParamsNode(val genericParams: List<GenericParamNode>) {
        companion object
    }

    data class FunctionParamsNode(val selfParam: SelfParamNode?, val functionParams: List<FunctionParamNode>) {
        companion object
    }

    class GenericParamNode
    class SelfParamNode
    class FunctionParamNode
}

fun ParamsNode.GenericParamsNode.Companion.parse(ctx: Context): ParamsNode.GenericParamsNode {
    return ParamsNode.GenericParamsNode(listOf())
}

fun ParamsNode.FunctionParamsNode.Companion.parse(ctx: Context): ParamsNode.FunctionParamsNode {
    return ParamsNode.FunctionParamsNode(null, listOf())
}