package rusty.semantic.support

import rusty.core.CompilerPointer
import rusty.core.utils.Slot

class Scope(val parent: Scope? = null, val children: MutableList<Scope> = mutableListOf(), val kind: ScopeKind , val annotation: Annotation) {
    enum class ScopeKind {
        Prelude,
        Trait,
        Crate,
        FunctionParams,
        FunctionBody,
        Implement,
        Repeat,
        Normal,
    }

    companion object {
        fun ofPrelude(): Scope {
            return Scope(
                parent = null,
                children = mutableListOf(),
                annotation = Annotation.from(null, "~Prelude"),
                kind = ScopeKind.Prelude,
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
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "str", (SemanticType.StringType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "cstr", (SemanticType.CStringType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "bool", (SemanticType.BoolType)
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
        return "Scope($annotation, ${kind.name})"
    }

    fun addChildScope(childPointer: CompilerPointer, childName: String? = null, childKind: ScopeKind): Scope {
        val childScope = Scope(parent = this, annotation = Annotation.from(childPointer, childName), kind = childKind)
        assert(children.add(childScope))
        return childScope
    }
}