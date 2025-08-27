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
                it.variableConstantST.declare(
                    SemanticSymbol.Variable(
                        "i32",
                        null,
                        mutable = Slot(false),
                        type = Slot(SemanticType.I32Type)
                    )
                )
                it.variableConstantST.declare(
                    SemanticSymbol.Variable(
                        "u32",
                        null,
                        mutable = Slot(false),
                        type = Slot(SemanticType.U32Type)
                    )
                )
                it.variableConstantST.declare(
                    SemanticSymbol.Variable(
                        "isize",
                        null,
                        mutable = Slot(false),
                        type = Slot(SemanticType.ISizeType)
                    )
                )
                it.variableConstantST.declare(
                    SemanticSymbol.Variable(
                        "usize",
                        null,
                        mutable = Slot(false),
                        type = Slot(SemanticType.USizeType)
                    )
                )
                it
            }
        }
    }

    val variableConstantST = SymbolTable()
    val functionST = SymbolTable()
    val structEnumST = SymbolTable()

    override fun toString(): String {
        return "Scope($annotation, @VC=$variableConstantST, @F=$functionST, @SE=$structEnumST)"
    }

    fun addChildScope(childPointer: CompilerPointer, childName: String? = null): Scope {
        val childScope = Scope(parent = this, annotation = Annotation.from(childPointer, childName))
        assert(children.add(childScope))
        return childScope
    }
}