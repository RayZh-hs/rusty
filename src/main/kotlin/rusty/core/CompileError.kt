package rusty.core

import com.andreapivetta.kolor.cyan

class CompileError(message: String) : Exception() {
    private var mutableMessage: String = message
    companion object {
        var source: List<String>? = null
        fun registerSource(externSource: List<String>) {
            CompileError.Companion.source = externSource.toList()
        }
    }

    override val message: String
        get() = mutableMessage

    /**
     * Appends context to the error message. Returns the current instance for chaining.
     */
    fun with(x: Any): CompileError {
        // 3. The 'with' method now correctly modifies our private property.
        mutableMessage += "\n$x"
        return this
    }

    fun at(ptr: CompilerPointer?): CompileError {
        if (ptr == null) return this
        val src = CompileError.Companion.source
        if (src != null && ptr.line - 1 in src.indices) {
            val lineContent = src[ptr.line - 1]
            val caretLine = buildString {
                repeat(maxOf(0, ptr.column - 1)) { append(' ') }
                append('^')
            }
            mutableMessage += "\nCompile Error occurred at position: ${ptr.line}:${ptr.column}\n    $lineContent\n    $caretLine"
        } else {
            mutableMessage += when(ptr) {
                CompilerPointer.forPrelude -> "\nCompile Error occurred at " + "~Prelude".cyan()
                CompilerPointer.forUnknown -> "\nCompile Error occurred at " + "~Unknown".cyan()
                else -> "\nCompile Error occurred at position ${ptr.line}:${ptr.column}"
            }
        }
        return this
    }
}