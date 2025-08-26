package rusty.semantic

import com.andreapivetta.kolor.blue
import com.andreapivetta.kolor.cyan
import com.andreapivetta.kolor.green
import com.andreapivetta.kolor.magenta
import com.andreapivetta.kolor.red
import com.andreapivetta.kolor.yellow
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticTypeNode
import rusty.semantic.support.Symbol
import rusty.parser.nodes.PatternNode
import rusty.parser.nodes.SupportingPatternNode
import java.io.File

// This file provides dump utilities for the SemanticConstructor module

// Render scope tree (only scopes and symbols), colorized
private fun Scope.renderTree(prefix: String = "", isLast: Boolean = true): String {
    val connector = if (isLast) "└─" else "├─"
    val nextPrefix = prefix + if (isLast) "  " else "│ "

    val name = (this.annotation.name ?: "<anon>").cyan()
    val kind = if (this.parent == null) "scope".magenta() else "scope".magenta()

    fun typeToStr(t: SemanticTypeNode?): String = when (t) {
        is SemanticTypeNode.I32Type -> "i32"
        is SemanticTypeNode.U32Type -> "u32"
        is SemanticTypeNode.ISizeType -> "isize"
        is SemanticTypeNode.USizeType -> "usize"
        is SemanticTypeNode.CharType -> "char"
        is SemanticTypeNode.StringType -> "str"
        is SemanticTypeNode.CStringType -> "cstr"
        is SemanticTypeNode.BoolType -> "bool"
        is SemanticTypeNode.UnitType -> "()"
        is SemanticTypeNode.ArrayType -> "[${typeToStr(t.elementType.getOrNull())}]"
        is SemanticTypeNode.StructType -> "struct ${t.identifier}"
        is SemanticTypeNode.EnumType -> "enum ${t.identifier}"
        is SemanticTypeNode.ReferenceType -> {
            if (t.isMutable.isReady()) {
                "&mut ${typeToStr(t.type.getOrNull())}"
            } else {
                "~"
            }
        }
        is SemanticTypeNode.SliceType -> "slice"
        null -> "~"
    }

    fun patternToName(p: PatternNode): String {
        // Best-effort extraction: take the first identifier if present; otherwise use "_"
        return when (val first = p.patternNodes.firstOrNull()) {
            is SupportingPatternNode.IdentifierPatternNode -> first.identifier
            else -> "_"
        }
    }

    fun paramListToStr(sym: Symbol.Function): String {
        val parts = mutableListOf<String>()
        // Self parameter (if any)
        val self = sym.selfParam.getOrNull()
        if (self != null) {
            val tStr = typeToStr(self.type.getOrNull())
            val refPrefix = if (self.isRef && self.isMut) "&mut " else if (self.isRef) "&" else ""
            parts += ("self".green() + ": " + (refPrefix + (tStr)))
        }
        // Regular parameters (if resolved)
        val params = sym.funcParams.getOrNull()
        params?.forEach { p ->
            val name = patternToName(p.pattern).green()
            val typeStr = typeToStr(p.type.getOrNull())
            // Try to respect ref/mut if the top-level pattern is an identifier
            val top = p.pattern.patternNodes.firstOrNull()
            val refPrefix = if (top is SupportingPatternNode.IdentifierPatternNode && top.isRef && top.isMut) {
                "&mut "
            } else if (top is SupportingPatternNode.IdentifierPatternNode && top.isRef) {
                "&"
            } else ""
            parts += "$name: ${refPrefix}$typeStr"
        }
        return parts.joinToString(", ")
    }

    val vc = if (variableConstantST.symbols.isEmpty()) "" else buildString {
        append(" ∘ ")
        append("vars:".yellow())
        append(" [")
        append(variableConstantST.symbols.values.joinToString(", ") { sym ->
            when (sym) {
                is Symbol.Variable -> (sym.identifier.green() + ":" + (sym.type.getOrNull()?.let { typeToStr(it) } ?: "_"))
                is Symbol.Const -> (sym.identifier.green() + ":" + (sym.type.getOrNull()?.let { typeToStr(it) } ?: "_"))
                else -> sym.identifier.green()
            }
        })
        append("]")
    }

    val fn = if (functionST.symbols.isEmpty()) "" else buildString {
        append(" ∘ ")
        append("fns:".blue())
        append(" [")
        append(functionST.symbols.values.joinToString(", ") {
            when (it) {
                is Symbol.Function -> {
                    val name = it.identifier.green()
                    val params = paramListToStr(it)
                    if (params.isNotEmpty()) "$name($params)" else "$name()"
                }
                else -> it.identifier.green()
            }
        })
        append("]")
    }

    val se = if (structEnumST.symbols.isEmpty()) "" else buildString {
        fun fieldsToStr(fields: Map<String, SemanticTypeNode>): String {
            if (fields.isEmpty()) return "{}"
            return fields.entries.joinToString(prefix = "{", postfix = "}", separator = ", ") { (n, t) ->
                n.green() + ": " + typeToStr(t)
            }
        }
        fun funcSig(f: Symbol.Function): String {
            val name = f.identifier.green()
            val params = paramListToStr(f)
            return if (params.isNotEmpty()) "$name($params)" else "$name()"
        }

        append(" ∘ ")
        append("types:".red())
        append(" [")
        append(structEnumST.symbols.values.joinToString(", ") { sym ->
            when (sym) {
                is Symbol.Struct -> buildString {
                    append(sym.identifier.green())
                    val vars = if (sym.variables.isReady()) sym.variables.getOrNull() else null
                    if (vars != null) append(" ").append(fieldsToStr(vars)) else append(" {}")

                    if (sym.functions.isNotEmpty()) {
                        append(" ∘ ")
                        append("fns:".blue())
                        append(" [")
                        append(sym.functions.values.joinToString(", ") { funcSig(it) })
                        append("]")
                    }
                    if (sym.constants.isNotEmpty()) {
                        append(" ∘ ")
                        append("consts:".yellow())
                        append(" [")
                        append(sym.constants.values.joinToString(", ") { c ->
                            c.identifier.green() + ": " + (c.type.getOrNull()?.let { typeToStr(it) } ?: "_")
                        })
                        append("]")
                    }
                }
                is Symbol.Enum -> buildString {
                    append(sym.identifier.green())
                    val elems = if (sym.elements.isReady()) sym.elements.getOrNull() else null
                    val content = elems?.joinToString(", ") { it.green() } ?: ""
                    append("{")
                    append(content)
                    append("}")
                }
                else -> sym.identifier.green()
            }
        })
        append("]")
    }

    val header = "$prefix$connector $kind $name$vc$fn$se\n"

    return buildString {
        append(header)
        children.forEachIndexed { idx, child ->
            val last = idx == children.lastIndex
            append(child.renderTree(nextPrefix, last))
        }
    }
}

fun SemanticConstructor.Companion.dump(output: OutputType, outputPath: String) {
    val file = File(outputPath)
    file.writeText(output.scopeTree.renderTree())
}

fun SemanticConstructor.Companion.dumpScreen(output: OutputType) {
    println("[rusty] Semantic scope tree:".green())
    print(output.scopeTree.renderTree())
    println()
}

fun SemanticConstructor.Companion.dumpPhase(label: String, output: OutputType, outputPath: String) {
    val file = File(outputPath)
    val header = "[rusty] Semantic dump ($label)\n"
    file.writeText(header + output.scopeTree.renderTree())
}

fun SemanticConstructor.Companion.dumpScreenPhase(label: String, output: OutputType) {
    println("[rusty] Semantic dump ($label):".cyan())
    print(output.scopeTree.renderTree())
    println()
}