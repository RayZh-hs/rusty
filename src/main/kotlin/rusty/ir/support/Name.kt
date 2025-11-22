package rusty.ir.support

import rusty.semantic.support.SemanticSymbol

/**
 * Utility for producing debug-friendly, unique identifiers following
 * `<holder>.<type>.<name>(.<serial>)`.
 *
 * The caller provides a [Renamer] to control serial numbering per function so
 * block/variable names restart for each body, while global helpers reuse an
 * internal renamer for module-scoped temps (e.g., string literals).
 */
class Name(val identifier: String) {
    companion object {
        private val globalRenamer = Renamer()

        fun reset() {
            globalRenamer.clearAll()
        }

        private fun holderPrefix(symbol: SemanticSymbol?): String {
            return if (symbol?.definedAt == null) "prelude" else "user"
        }

        private fun withSerial(base: String, renamer: Renamer): String {
            val serial = renamer.next(base)
            return "$base.$serial"
        }

        fun ofStruct(identifier: String, symbol: SemanticSymbol? = null): Name {
            return Name("${holderPrefix(symbol)}.struct.$identifier")
        }

        fun ofFunction(
            symbol: SemanticSymbol.Function,
            owner: String? = null,
            enclosing: Name? = null,
        ): Name {
            val baseName = buildString {
                append(holderPrefix(symbol))
                append(".func.")
                if (owner != null) append(owner).append(".")
                append(symbol.identifier)
            }
            val qualified = enclosing?.let { "${it.identifier}\$${symbol.identifier}" } ?: baseName
            return Name(qualified)
        }

        fun ofVariable(symbol: SemanticSymbol.Variable, renamer: Renamer): Name {
            val base = "${holderPrefix(symbol)}.var.${symbol.identifier}"
            return Name(withSerial(base, renamer))
        }

        fun auxSelf(): Name = Name("aux.var.self")
        fun auxReturn(): Name = Name("aux.var.ret")

        fun block(renamer: Renamer): Name = Name(withSerial("aux.block", renamer))

        fun blockResult(renamer: Renamer): Name = Name(withSerial("aux.var.blockret", renamer))

        fun auxTemp(prefix: String, renamer: Renamer, type: String = "var"): Name =
            Name(withSerial("aux.$type.$prefix", renamer))

        fun auxTempGlobal(prefix: String): Name = Name(withSerial("aux.var.$prefix", globalRenamer))
    }
}
