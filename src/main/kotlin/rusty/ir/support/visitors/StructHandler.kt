package rusty.ir.support.visitors

import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import space.norb.llvm.types.TypeUtils

class StructHandler(val semanticContext: SemanticContext) {

    /**
     * @brief Handles the struct and constant definitions in the IR generation phase.
     * */
    fun run() {
        val structs = semanticContext.scopeTree.typeST.symbols.filter {
            it.value is SemanticSymbol.Struct
        }
        // first pass: register struct types
        for ((_, symbol) in structs) {
            val symbol = symbol as SemanticSymbol.Struct
            val name = Name.ofStruct(symbol.identifier)
            val type = IRContext.module.registerNamedStructType(
                name = name.identifier,
                elementTypes = null, // to be filled in
                isPacked = true
            )
            IRContext.structTypeLookup[symbol.identifier] = type
        }
        // second pass: fill struct bodies
        for ((_, symbol) in structs) {
            val symbol = symbol as SemanticSymbol.Struct
            val fieldTypes = buildList {
                for ((fieldName, fieldTypeSlot) in symbol.fields) {
                    val fieldType = fieldTypeSlot.getOrNull()
                        ?: throw IllegalStateException("Struct field type is not resolved: ${symbol.identifier}.$fieldName")
                    add(fieldType.toIRType())
                }
            }.ifEmpty { listOf(TypeUtils.I8) }

            val name = Name.ofStruct(symbol.identifier).identifier
            val completedType = IRContext.module.completeOpaqueStructType(
                name = name,
                elementTypes = fieldTypes,
                isPacked = true
            )
            IRContext.structTypeLookup[symbol.identifier] = completedType
        }
    }
}
