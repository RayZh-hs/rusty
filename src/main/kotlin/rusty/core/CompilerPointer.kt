package rusty.core

/**
 * Represents a precise position in the original (pre-preprocessed) source file.
 * ! Line & column are 1-based indices.
 */
data class CompilerPointer(val line: Int, val column: Int) {
    companion object {
        val forPrelude = CompilerPointer(-1, 0)
        val forUnknown = CompilerPointer(-1, 1)
    }
	override fun toString(): String = "${line}:${column}"
}