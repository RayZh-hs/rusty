package rusty.semantic

import com.andreapivetta.kolor.blue
import com.andreapivetta.kolor.cyan
import com.andreapivetta.kolor.green
import com.andreapivetta.kolor.greenBackground
import com.andreapivetta.kolor.magenta
import com.andreapivetta.kolor.red
import com.andreapivetta.kolor.yellow
import rusty.semantic.support.Scope
import rusty.semantic.support.SemanticType
import rusty.semantic.support.SemanticSymbol
import rusty.semantic.support.SemanticValue
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

    fun typeToStr(t: SemanticType?): String = when (t) {
        is SemanticType.I32Type -> "i32"
        is SemanticType.U32Type -> "u32"
        is SemanticType.ISizeType -> "isize"
        is SemanticType.USizeType -> "usize"
        is SemanticType.AnyIntType -> "anyint"
        is SemanticType.AnySignedIntType -> "anysint"
        is SemanticType.CharType -> "char"
        is SemanticType.StringType -> "str"
        is SemanticType.CStringType -> "cstr"
        is SemanticType.BoolType -> "bool"
        is SemanticType.UnitType -> "unit"
        is SemanticType.WildcardType -> "_"
        is SemanticType.ArrayType -> {
            val elem = typeToStr(t.elementType.getOrNull())
            val len = if (t.length.isReady()) t.length.get().value.toString() else "_"
            "[${elem}; ${len}]"
        }
        is SemanticType.StructType -> "struct ${t.identifier}"
        is SemanticType.EnumType -> "enum ${t.identifier}"
        is SemanticType.ReferenceType -> {
            if (t.isMutable.isReady()) {
                "&mut ${typeToStr(t.type.getOrNull())}"
            } else {
                "~"
            }
        }
        null -> "~"
    }

    fun valueToStr(v: SemanticValue): String = when (v) {
        is SemanticValue.I32Value -> v.value.toString()
        is SemanticValue.U32Value -> v.value.toString()
        is SemanticValue.ISizeValue -> v.value.toString()
        is SemanticValue.USizeValue -> v.value.toString()
        is SemanticValue.AnyIntValue -> v.value.toString()
        is SemanticValue.AnySignedIntValue -> v.value.toString()
        is SemanticValue.CharValue -> "'${v.value}'"
        is SemanticValue.StringValue -> "\"${v.value}\""
        is SemanticValue.CStringValue -> "c\"${v.value}\""
        is SemanticValue.BoolValue -> v.value.toString()
        is SemanticValue.UnitValue -> "()"
        is SemanticValue.ArrayValue -> buildString {
            val n = if (v.elements.isNotEmpty()) v.elements.size else v.repeat.value.toInt()
            append("#array(len=").append(n).append(")")
        }
        is SemanticValue.StructValue -> buildString {
            append("#struct {")
            append(v.fields.entries.joinToString(", ") { (k, sv) -> k.green() + ": " + valueToStr(sv) })
            append("}")
        }
        is SemanticValue.EnumValue -> "#enum ${v.field}"
        is SemanticValue.ReferenceValue -> "&" + valueToStr(v.referenced)
    }

    fun patternToName(p: PatternNode): String {
        // Best-effort extraction: take the first identifier if present; otherwise use "_"
        return when (val first = p.patternNodes.firstOrNull()) {
            is SupportingPatternNode.IdentifierPatternNode -> first.identifier
            else -> "_"
        }
    }

    fun paramListToStr(sym: SemanticSymbol.Function): String {
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
            val paramName = patternToName(p.pattern).green()
            val typeStr = typeToStr(p.type.getOrNull())
            // Try to respect ref/mut if the top-level pattern is an identifier
            val top = p.pattern.patternNodes.firstOrNull()
            val refPrefix = if (top is SupportingPatternNode.IdentifierPatternNode && top.isRef && top.isMut) {
                "&mut "
            } else if (top is SupportingPatternNode.IdentifierPatternNode && top.isRef) {
                "&"
            } else ""
            parts += "$paramName: ${refPrefix}$typeStr"
        }
        return parts.joinToString(", ")
    }

    val vc = if (variableST.symbols.isEmpty()) "" else buildString {
        append(" ∘ ")
        append("vars:".yellow())
        append(" [")
        append(variableST.symbols.values.joinToString(", ") { sym ->
            when (sym) {
                is SemanticSymbol.Variable -> (sym.identifier.green() + ":" + (sym.type.getOrNull()?.let { typeToStr(it) } ?: "_"))
                is SemanticSymbol.Const -> buildString {
                    append(sym.identifier.green())
                    append(":")
                    append(sym.type.getOrNull()?.let { typeToStr(it) } ?: "_")
                    val v = sym.value.getOrNull()
                    if (v != null) {
                        append(" = ")
                        append(valueToStr(v))
                    }
                }
                else -> sym.identifier.green()
            }
        })
        append("]")
    }

    val fn = if (functionST.symbols.isEmpty()) "" else buildString {
        append(" ∘ ")
        append("fns:".blue())
        append(" [")
        append(functionST.symbols.values.joinToString(", ") { func ->
            when (func) {
                is SemanticSymbol.Function -> {
                    val funcName = func.identifier.green()
                    val params = paramListToStr(func)
                    val retType = func.returnType.getOrNull()?.let { typeToStr(it) } ?: "~"
                    if (params.isNotEmpty()) "$funcName($params) -> $retType" else "$funcName() -> $retType"
                }
                else -> func.identifier.green()
            }
        })
        append("]")
    }

    val se = if (typeST.symbols.isEmpty()) "" else buildString {
        fun fieldsToStr(fields: Map<String, rusty.core.utils.Slot<SemanticType>>): String {
            if (fields.isEmpty()) return "{}"
            return fields.entries.joinToString(prefix = "{", postfix = "}", separator = ", ") { (n, slot) ->
                n.green() + ": " + (slot.getOrNull()?.let { typeToStr(it) } ?: "_")
            }
        }
        fun funcSig(f: SemanticSymbol.Function): String {
            val sigName = f.identifier.green()
            val params = paramListToStr(f)
            return if (params.isNotEmpty()) "$sigName($params)" else "$sigName()"
        }

        append(" ∘ ")
        append("types:".red())
        append(" [")
        append(typeST.symbols.values.joinToString(", ") { sym ->
            when (sym) {
                is SemanticSymbol.Struct -> buildString {
                    append(sym.identifier.green())
                    append(" ")
                    append(fieldsToStr(sym.definesType.fields))

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
                            buildString {
                                append(c.identifier.green())
                                append(": ")
                                append(c.type.getOrNull()?.let { typeToStr(it) } ?: "_")
                                val v = c.value.getOrNull()
                                if (v != null) {
                                    append(" = ")
                                    append(valueToStr(v))
                                }
                            }
                        })
                        append("]")
                    }
                }
                is SemanticSymbol.Enum -> buildString {
                    append(sym.identifier.green())
                    val elems = sym.definesType.fields.getOrNull()
                    val content = elems?.joinToString(", ") { it.green() } ?: ""
                    append("{")
                    append(content)
                    append("}")

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
                            buildString {
                                append(c.identifier.green())
                                append(": ")
                                append(c.type.getOrNull()?.let { typeToStr(it) } ?: "_")
                                val v = c.value.getOrNull()
                                if (v != null) {
                                    append(" = ")
                                    append(valueToStr(v))
                                }
                            }
                        })
                        append("]")
                    }
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
    println("[rusty] Semantic dump ".green() + "($label):".cyan())
    print(output.scopeTree.renderTree())
    println()
}