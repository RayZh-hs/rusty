package rusty.core

class CompileError(message: String) : Exception() {
    private var mutableMessage: String = message

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
}