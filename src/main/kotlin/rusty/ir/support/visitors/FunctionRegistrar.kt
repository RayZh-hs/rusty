package rusty.ir.support.visitors

import rusty.ir.support.FunctionPlanBuilder
import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.visitors.pattern.ParameterNameExtractor
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticType
import rusty.core.CompileError
import rusty.semantic.visitors.bases.ScopeAwareVisitorBase
import rusty.semantic.visitors.utils.sequentialLookup
import space.norb.llvm.enums.LinkageType

/**
 * Registers all user-defined functions (free + methods) on the module and
 * records their plans for later body emission.
 */
class FunctionRegistrar(ctx: SemanticContext) : ScopeAwareVisitorBase(ctx) {

    override fun visitFunctionItem(node: rusty.parser.nodes.ItemNode.FunctionItemNode) {
        val containerScope = currentScope()
        val symbol = containerScope.functionST.resolve(node.identifier) as? SemanticSymbol.Function
            ?: sequentialLookup(node.identifier, containerScope) { it.functionST }?.symbol as? SemanticSymbol.Function
            ?: throw IllegalStateException("Unresolved function symbol for ${node.identifier}")
        val renamer = IRContext.renamerFor(symbol).also { it.clearAll() }

        val ownerName = symbol.selfParam.getOrNull()?.let { self ->
            when (val ty = self.type.getOrNull()) {
                is SemanticType.StructType -> ty.identifier
                is SemanticType.EnumType -> ty.identifier
                is SemanticType.TraitType -> ty.identifier
                else -> null
            }
        } ?: findAssociatedOwner(symbol)

        val registerPlan: (rusty.semantic.support.Scope) -> Unit = { funcScope ->
            val extractor = ParameterNameExtractor(funcScope, renamer)
            val plan = FunctionPlanBuilder.build(symbol, ownerName, renamer, extractor)

            val linkage = LinkageType.EXTERNAL
            val fn = IRContext.module.registerFunction(plan.name.identifier, plan.type, linkage, false)

            IRContext.functionPlans[symbol] = plan
            IRContext.functionNameLookup[symbol] = plan.name
            IRContext.functionLookup[symbol] = fn
        }

        try {
            scopeMaintainer.withNextScope { funcScope ->
                registerPlan(funcScope)
                // Continue visiting the function body to find nested functions
                visitFunctionInternal(node)
            }
        } catch (e: CompileError) {
            registerPlan(containerScope)
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

    private fun findAssociatedOwner(symbol: SemanticSymbol.Function): String? {
        fun search(scope: rusty.semantic.support.Scope): String? {
            scope.typeST.symbols.values.forEach { typeSymbol ->
                val functions = when (typeSymbol) {
                    is SemanticSymbol.Struct -> typeSymbol.functions.values
                    is SemanticSymbol.Enum -> typeSymbol.functions.values
                    is SemanticSymbol.Trait -> typeSymbol.functions.values
                    else -> null
                }
                if (functions != null && functions.any { it === symbol }) {
                    return typeSymbol.identifier
                }
            }
            scope.children.forEach { child ->
                val match = search(child)
                if (match != null) return match
            }
            return null
        }
        return search(ctx.scopeTree)
    }
}
