package rusty.semantic.visitors

import rusty.core.CompileError
import rusty.parser.nodes.ItemNode
import rusty.parser.nodes.support.FunctionParamNode
import rusty.semantic.support.Context
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.companions.SelfResolverCompanion
import rusty.semantic.visitors.companions.StaticResolverCompanion

class ItemTypeResolverVisitor(ctx: Context) : ScopeAwareVisitorBase(ctx) {
    val staticResolver: StaticResolverCompanion = StaticResolverCompanion(ctx)
    val selfResolver: SelfResolverCompanion = SelfResolverCompanion()

    override fun visitConstItem(node: ItemNode.ConstItemNode) {
        staticResolver.resolveConstItem(node.identifier, currentScope())
    }

    override fun visitFunctionItem(node: ItemNode.FunctionItemNode) {
        // process the parameters
        node.functionParamsNode.selfParam?.let {
            val selfSymbol = selfResolver.getSelf()
                ?: throw CompileError("'self' parameter found outside of an impl block")
                    .with(it).at(node.pointer)
            when (selfSymbol) {
                is SemanticSymbol.Struct -> TODO()
                else -> throw CompileError("'self' parameter found outside of a struct impl block")
                    .with(it).at(node.pointer)
            }
        }
        node.functionParamsNode.functionParams.forEach { param ->
            // resolve the type of the parameter
            when (param) {
                is FunctionParamNode.FunctionParamTypedPatternNode -> {
                    val type = staticResolver.resolveTypeNode(
                        param.type ?: throw CompileError("Missing type annotation for function parameter")
                            .with(param).at(node.pointer),
                        currentScope())

                }
                else -> throw CompileError("Unsupported function parameter pattern")
                    .with(param).at(node.pointer)
            }
        }
    }

    override fun visitStructItem(node: ItemNode.StructItemNode) {
        val scope = currentScope()
        val resolvedSymbol = scope.typeST.resolve(node.identifier)
            ?: throw CompileError("Unresolved struct type: ${node.identifier}")
                .with(node.identifier).at(node.pointer)
        selfResolver.withinSymbol(resolvedSymbol) {
            super.visitStructItem(node)
        }
    }
}