package pub

enum class CompileMode {
    PREPROCESS, LEX,
}

val CompileModeMap = mapOf(
    "pp" to CompileMode.PREPROCESS,
    "preprocess" to CompileMode.PREPROCESS,
    "lex" to CompileMode.LEX,
)

enum class DisplayMode {
    NONE, RESULT, VERBOSE
}

val DisplayModeMap = mapOf(
    "none" to DisplayMode.NONE,
    "result" to DisplayMode.RESULT,
    "verbose" to DisplayMode.VERBOSE
)