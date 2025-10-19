package rusty.semantic.support

import rusty.core.CompilerPointer
import rusty.core.utils.Slot
import rusty.semantic.visitors.utils.funcParamsFromMap

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
                        "char", (SemanticType.CharType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "str", (SemanticType.StrType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "cstr", (SemanticType.CStrType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.BuiltinType(
                        "bool", (SemanticType.BoolType)
                    )
                )
                it.typeST.declare(
                    SemanticSymbol.Struct(
                        identifier = "String",
                        definedAt = null,
                        definesType = SemanticType.StringStructType,
                        functions = mutableMapOf<String, SemanticSymbol.Function>(
                            "as_str" to SemanticSymbol.Function(
                                identifier = "as_str",
                                definedAt = null,
                                selfParam = Slot(SemanticSelfNode(isMut = false, isRef = true)),
                                funcParams = Slot(listOf()),
                                returnType = Slot(SemanticType.RefStrType)
                            ),
                            "as_mut_str" to SemanticSymbol.Function(
                                identifier = "as_mut_str",
                                definedAt = null,
                                selfParam = Slot(SemanticSelfNode(isMut = true, isRef = true)),
                                funcParams = Slot(listOf()),
                                returnType = Slot(SemanticType.RefMutStrType)
                            ),
                            "len" to SemanticSymbol.Function(
                                identifier = "len",
                                definedAt = null,
                                selfParam = Slot(SemanticSelfNode(isMut = false, isRef = true)),
                                funcParams = Slot(listOf()),
                                returnType = Slot(SemanticType.USizeType)
                            )
                        ),
                        constants = mutableMapOf<String, SemanticSymbol.Const>()
                    ).also { sym ->
                        sym.functions["as_str"]?.selfParam?.get()?.type?.set(sym.definesType)
                        sym.functions["as_str"]?.selfParam?.get()?.symbol?.set(sym)
                        sym.functions["as_mut_str"]?.selfParam?.get()?.type?.set(sym.definesType)
                        sym.functions["as_mut_str"]?.selfParam?.get()?.symbol?.set(sym)
                    }
                )

                it.functionST.declare(
                    SemanticSymbol.Function(
                        "print", null,
                        selfParam = Slot(null),
                        funcParams = Slot(funcParamsFromMap(
                            mapOf("s" to SemanticType.RefStrType),
                            pointer = CompilerPointer.forPrelude
                        )),
                        returnType = Slot(SemanticType.UnitType)
                    )
                )
                it.functionST.declare(
                    SemanticSymbol.Function(
                        "println", null,
                        selfParam = Slot(null),
                        funcParams = Slot(funcParamsFromMap(
                            mapOf("s" to SemanticType.RefStrType),
                            pointer = CompilerPointer.forPrelude
                        )),
                        returnType = Slot(SemanticType.UnitType)
                    )
                )
                it.functionST.declare(
                    SemanticSymbol.Function(
                        "printInt", null,
                        selfParam = Slot(null),
                        funcParams = Slot(funcParamsFromMap(
                            mapOf("n" to SemanticType.I32Type),
                            pointer = CompilerPointer.forPrelude
                        )),
                        returnType = Slot(SemanticType.UnitType)
                    )
                )
                it.functionST.declare(
                    SemanticSymbol.Function(
                        "printlnInt", null,
                        selfParam = Slot(null),
                        funcParams = Slot(funcParamsFromMap(
                            mapOf("n" to SemanticType.I32Type),
                            pointer = CompilerPointer.forPrelude
                        )),
                        returnType = Slot(SemanticType.UnitType)
                    )
                )
                it.functionST.declare(
                    SemanticSymbol.Function(
                        "getString", null,
                        selfParam = Slot(null),
                        funcParams = Slot(listOf()),
                        returnType = Slot(SemanticType.StringStructType)
                    )
                )
                it.functionST.declare(
                    SemanticSymbol.Function(
                        "getInt", null,
                        selfParam = Slot(null),
                        funcParams = Slot(listOf()),
                        returnType = Slot(SemanticType.I32Type)
                    )
                )
                it.functionST.declare(
                    SemanticSymbol.Function(
                        "exit", null,
                        selfParam = Slot(null),
                        funcParams = Slot(funcParamsFromMap(
                            mapOf("code" to SemanticType.I32Type),
                            pointer = CompilerPointer.forPrelude
                        )),
                        returnType = Slot(SemanticType.ExitType)
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

    fun toShortString(): String {
        return "${kind.name}(${annotation.pointer?.line}:${annotation.pointer?.column})"
    }

    fun addChildScope(childPointer: CompilerPointer, childName: String? = null, childKind: ScopeKind): Scope {
        val childScope = Scope(parent = this, annotation = Annotation.from(childPointer, childName), kind = childKind)
        assert(children.add(childScope))
        return childScope
    }
}