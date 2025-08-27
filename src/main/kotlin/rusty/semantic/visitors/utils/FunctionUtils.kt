package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.core.utils.Slot
import rusty.parser.nodes.ItemNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticFunctionParamNode
import rusty.semantic.support.SemanticSelfNode
import rusty.semantic.support.SemanticSymbol

fun newFunctionSignature(ctx: Context, node: ItemNode.FunctionItemNode): SemanticSymbol.Function {
    val selfParam = node.functionParamsNode.selfParam?.let { SemanticSelfNode.from(it) }
    val funcParams = node.functionParamsNode.functionParams.map {
        when (it) {
            is rusty.parser.nodes.support.FunctionParamNode.FunctionParamTypedPatternNode -> SemanticFunctionParamNode(
                pattern = it.pattern,
            )
            else -> throw CompileError("Removed from Spec: Unexpected function parameter type $it")
                .with(ctx).at(node.functionParamsNode.pointer)
        }
    }
    return SemanticSymbol.Function(
        identifier = node.identifier,
        definedAt = node,
        selfParam = Slot(selfParam),
        funcParams = Slot(funcParams),
    )
}