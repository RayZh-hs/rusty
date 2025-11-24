package rusty.ir.support.visitors

import rusty.ir.support.FunctionPlanBuilder
import rusty.ir.support.IRContext
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import space.norb.llvm.types.PointerType

/**
 * Declares prelude functions as external so they can be called from user code.
 * No bodies are generated here.
 */
class PreludeHandler(private val semanticContext: SemanticContext) {
    fun run() {
        val preludeFunctions = semanticContext.scopeTree.functionST.symbols.values
            .filterIsInstance<SemanticSymbol.Function>()
        for (function in preludeFunctions) {
            val renamer = IRContext.renamerFor(function).also { it.clearAll() }
            val plan = FunctionPlanBuilder.build(function, ownerName = null, renamer = renamer, paramNameExtractor = null)
            val fn = IRContext.module.declareExternalFunction(plan.name.identifier, plan.type)
            IRContext.functionPlans[function] = plan
            IRContext.functionNameLookup[function] = plan.name
            IRContext.functionLookup[function] = fn
        }
        val irStringStruct = IRContext.module.registerNamedStructType(
            name = "prelude.struct.String",
            elementTypes = listOf(PointerType),
            isPacked = false,
        )
        IRContext.structTypeLookup["String"] = irStringStruct
    }
}
