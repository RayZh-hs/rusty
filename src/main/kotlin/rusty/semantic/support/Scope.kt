package rusty.semantic.support

import rusty.core.CompilerPointer
import rusty.core.utils.Slot

class Scope(val parent: Scope? = null, val children: MutableList<Scope> = mutableListOf(), val annotation: Annotation) {
    companion object {
        fun from(parent: Scope?, name: String?, pointer: CompilerPointer?): Scope {
            return Scope(
                parent = parent,
                children = mutableListOf(),
                annotation = Annotation.from(pointer, name)
            )
        }

        fun ofPrelude(): Scope {
            return Scope(
                parent = null,
                children = mutableListOf(),
                annotation = Annotation.from(null, "Prelude")
            ).let {
                // TODO add all the prelude signatures
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "i32", (SemanticType.I32Type)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "u32", (SemanticType.U32Type)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "isize", (SemanticType.ISizeType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "usize", (SemanticType.USizeType)
                    )
                )
                it
            }
        }
    }

    val variableST = SymbolTable()  // holds variables and constants
    val functionST = SymbolTable()  // holds functions
    val typeST = SymbolTable()      // holds types (structs, enums, type aliases, traits, etc.)

    override fun toString(): String {
        return "Scope($annotation, @VC=$variableST, @F=$functionST, @SE=$typeST)"
    }

    fun addChildScope(childPointer: CompilerPointer, childName: String? = null): Scope {
        val childScope = Scope(parent = this, annotation = Annotation.from(childPointer, childName))
        assert(children.add(childScope))
        return childScope
    }
}