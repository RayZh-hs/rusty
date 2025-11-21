package rusty.ir.support.visitors

import rusty.ir.support.FunctionPlanBuilder
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.visitors.pattern.ParameterNameExtractor
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import space.norb.llvm.enums.LinkageType

/**
 * Registers all user-defined functions (free + methods) on the module and
 * records their plans for later body emission.
 */
class FunctionRegistrar(ctx: SemanticContext) : ScopeAwareVisitorBase(ctx) {

    override fun visitFunctionItem(node: rusty.parser.nodes.ItemNode.FunctionItemNode) {
        val containerScope = currentScope()
        val symbol = containerScope.functionST.resolve(node.identifier) as? SemanticSymbol.Function
            ?: throw IllegalStateException("Unresolved function symbol for ${node.identifier}")
        scopeMaintainer.withNextScope { funcScope ->

            val ownerName = symbol.selfParam.getOrNull()?.let { self ->
                when (val ty = self.type.getOrNull()) {
                    is SemanticType.StructType -> ty.identifier
                    is SemanticType.EnumType -> ty.identifier
                    else -> null
                }
            }
            val extractor = ParameterNameExtractor(funcScope)
            val plan = FunctionPlanBuilder.build(symbol, ownerName, extractor)

            val linkage = if (symbol.definedAt == null) LinkageType.EXTERNAL else LinkageType.INTERNAL
            val fn = IRContext.module.registerFunction(plan.name.identifier, plan.type, linkage, false)

            IRContext.functionPlans[symbol] = plan
            IRContext.functionNameLookup[symbol] = plan.name
            IRContext.functionLookup[symbol] = fn
        }
    }

    override fun visitTraitItem(node: rusty.parser.nodes.ItemNode.TraitItemNode) {
        scopeMaintainer.withNextScope {
            super.visitTraitItemInternal(node)
        }
    }

    override fun visitInherentImplItem(node: rusty.parser.nodes.ItemNode.ImplItemNode.InherentImplItemNode) {
        scopeMaintainer.withNextScope {
            super.visitInherentImplItem(node)
        }
    }

    override fun visitTraitImplItem(node: rusty.parser.nodes.ItemNode.ImplItemNode.TraitImplItemNode) {
        scopeMaintainer.withNextScope {
            super.visitTraitImplItem(node)
        }
    }
}
