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
        is SemanticTypeNode.BoolType -> "bool"
        is SemanticTypeNode.UnitType -> "()"
        is SemanticTypeNode.ArrayType -> "[${typeToStr(t.elementType.getOrNull())}]"
        is SemanticTypeNode.StructType -> "struct ${t.identifier}"
        is SemanticTypeNode.EnumType -> "enum ${t.identifier}"
        is SemanticTypeNode.ReferenceType -> {
            if (t.isMutable.isReady()) {
                "&mut ${typeToStr(t.type.getOrNull())}"
            } else {
                "unknown_ref"
            }
        }
        is SemanticTypeNode.SliceType -> "slice"
        null -> "null"
        else -> "unknown"
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
        append(functionST.symbols.values.joinToString(", ") { it.identifier.green() })
        append("]")
    }

    val se = if (structEnumST.symbols.isEmpty()) "" else buildString {
        append(" ∘ ")
        append("types:".red())
        append(" [")
        append(structEnumST.symbols.values.joinToString(", ") { it.identifier.green() })
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