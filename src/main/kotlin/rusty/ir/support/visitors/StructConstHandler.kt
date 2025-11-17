package rusty.ir.support.visitors

import rusty.ir.support.IRContext
import rusty.ir.support.Naming
import rusty.ir.support.toIRType
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol

class StructConstHandler(val semanticContext: SemanticContext) {

    /**
     * @brief Handles the struct and constant definitions in the IR generation phase.
     * */
    fun run() {
        handleStruct()
        handleConst()
    }

    private fun handleStruct() {
        val module = IRContext.module
        val structs = semanticContext.scopeTree.typeST.symbols.filter {
            it.value is SemanticSymbol.Struct
        }
        for ((_, symbol) in structs) {
            val symbol = symbol as SemanticSymbol.Struct
            val name = Naming.ofStruct(symbol.identifier)
            IRContext.structNameLookup[symbol] = name
            IRContext.module.registerNamedStructType(
                name = name,
                elementTypes = symbol.fields.map {
                    it.value.get().toIRType()   // convert to IR type
                }
            )
        }
    }

    private fun handleConst() {
        val module = IRContext.module
    }
}