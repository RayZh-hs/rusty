package rusty.core

enum class CompileMode {
    PREPROCESS, LEX, PARSE, SEMANTIC, IR,
}

val CompileModeMap = mapOf(
    "pp" to CompileMode.PREPROCESS,
    "pre" to CompileMode.PREPROCESS,
    "preprocess" to CompileMode.PREPROCESS,
    "lex" to CompileMode.LEX,
    "parse" to CompileMode.PARSE,
    "parser" to CompileMode.PARSE,
    "parsing" to CompileMode.PARSE,
    "sem" to CompileMode.SEMANTIC,
    "semantic" to CompileMode.SEMANTIC,
    "ir" to CompileMode.IR,
    "llvm" to CompileMode.IR,
    "ll" to CompileMode.IR,
)

enum class DisplayMode {
    NONE, RESULT, VERBOSE
}

val DisplayModeMap = mapOf(
    "none" to DisplayMode.NONE,
    "result" to DisplayMode.RESULT,
    "verbose" to DisplayMode.VERBOSE
)
