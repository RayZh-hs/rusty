package rusty.ir.support

import rusty.semantic.support.SemanticSymbol
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for producing debug-friendly, unique identifiers following
 * `<holder>.<type>.<name>(.<serial>)`.
 */
class Name(val identifier: String) {
    companion object {
        private val localCounter = ConcurrentHashMap<String, Int>()

        fun reset() {
            localCounter.clear()
        }

        private fun holderPrefix(symbol: SemanticSymbol?): String {
            return if (symbol?.definedAt == null) "prelude" else "user"
        }

        private fun unique(base: String, enableSerial: Boolean = true): String {
            if (!enableSerial) return base
            val next = localCounter.merge(base, 0) { old, _ -> old } ?: 0
            localCounter[base] = next + 1
            return "$base.$next"
        }

        fun ofStruct(identifier: String, symbol: SemanticSymbol? = null): Name {
            return Name("${holderPrefix(symbol)}.struct.$identifier")
        }

        fun ofFunction(symbol: SemanticSymbol.Function, owner: String? = null): Name {
            val baseName = buildString {
                append(holderPrefix(symbol))
                append(".func.")
                if (owner != null) append(owner).append(".")
                append(symbol.identifier)
            }
            return Name(baseName)
        }

        fun ofVariable(symbol: SemanticSymbol.Variable, allowSerial: Boolean = true): Name {
            val base = "${holderPrefix(symbol)}.var.${symbol.identifier}"
            return Name(unique(base, allowSerial))
        }

        fun auxTemp(prefix: String? = null, type: String = "var"): Name {
            val base = buildString {
                append("aux.")
                append(type)
                append(".")
                append(prefix ?: "tmp")
            }
            return Name(unique(base))
        }

        fun auxSelf(): Name = Name("aux.var.self")
        fun auxReturn(): Name = Name("aux.var.ret")

        fun ofBlock(scopePath: List<String>): Name {
            val base = "user.block.${scopePath.joinToString(".")}"
            return Name(unique(base))
        }
    }
}
