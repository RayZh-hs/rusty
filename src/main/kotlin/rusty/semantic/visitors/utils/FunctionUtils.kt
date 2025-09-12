package rusty.semantic.visitors.utils

import rusty.core.CompileError
import rusty.core.CompilerPointer
import rusty.core.utils.Slot
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticFunctionParamNode
import rusty.semantic.support.SemanticSelfNode
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType

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

fun SemanticSymbol.Function.isClassMethod() = this.selfParam.get() == null
fun SemanticSymbol.Function.isObjectMethod() = this.selfParam.get() != null

// Used only for built-in functions, to create function parameters from a map of names to types
fun funcParamsFromMap(map: Map<String, SemanticType>, pointer: CompilerPointer): List<SemanticFunctionParamNode> {
    val params = mutableListOf<SemanticFunctionParamNode>()
    for ((key, value) in map) {
        val pattern = PatternNode(
            patternNodes = listOf(SupportingPatternNode.IdentifierPatternNode(
                identifier = key,
                isRef = false,
                isMut = false,
                extendedByPatternNode = null,
                pointer = pointer,
            )),
            pointer = pointer,
        )
        params.add(SemanticFunctionParamNode(pattern = pattern, type = Slot(value)))
    }
    return params
}