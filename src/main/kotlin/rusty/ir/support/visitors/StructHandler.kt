package rusty.ir.support.visitors

import rusty.ir.support.IRContext
import rusty.ir.support.Name
import rusty.ir.support.toIRType
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticContext
import rusty.semantic.support.SemanticSymbol
import space.norb.llvm.types.TypeUtils

class StructHandler(val semanticContext: SemanticContext) {

    // SkipSelf used to skip the prelude
    private fun getAllStructs(scope: Scope, skipPrelude: Boolean = true): Set<SemanticSymbol.Struct> {
        val ret = mutableSetOf<SemanticSymbol.Struct>()
        for (i in scope.children) {
            ret.addAll(getAllStructs(i))
        }
        if (!skipPrelude || scope.kind != Scope.ScopeKind.Prelude) {
            ret.addAll(
                scope.typeST.symbols.values.filterIsInstance<SemanticSymbol.Struct>()
            )
        }
        return ret
    }

    /**
     * @brief Handles the struct and constant definitions in the IR generation phase.
     * */
    fun run() {
        val structs = getAllStructs(semanticContext.scopeTree, true).associateBy {
            it.identifier
        }
        // first pass: register struct types
        for ((_, symbol) in structs) {
            val name = Name.ofStruct(symbol.identifier)
            val type = IRContext.module.registerOpaqueStructType(
                name = name.identifier,
            )
            IRContext.structTypeLookup[symbol.identifier] = type
        }
        // second pass: fill struct bodies
        for ((_, symbol) in structs) {
            val fieldTypes = buildList {
                for ((fieldName, fieldTypeSlot) in symbol.fields) {
                    val fieldType = fieldTypeSlot.getOrNull()
                        ?: throw IllegalStateException("Struct field type is not resolved: ${symbol.identifier}.$fieldName")
                    add(fieldType.toIRType())
                }
            }.ifEmpty { listOf(TypeUtils.I8) }

            val name = Name.ofStruct(symbol.identifier)
            val completedType = IRContext.module.completeOpaqueStructType(
                name = name.identifier,
                elementTypes = fieldTypes,
                isPacked = false,
            )
            IRContext.structTypeLookup[symbol.identifier] = completedType
        }
    }
}
