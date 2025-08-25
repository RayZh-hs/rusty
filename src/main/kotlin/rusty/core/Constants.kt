package rusty.core

enum class CompileMode {
    PREPROCESS, LEX, PARSE, SEMANTIC,
}

val CompileModeMap = mapOf(
    "pp" to CompileMode.PREPROCESS,
    "preprocess" to CompileMode.PREPROCESS,
    "lex" to CompileMode.LEX,
    "parse" to CompileMode.PARSE,
    "parser" to CompileMode.PARSE,
    "parsing" to CompileMode.PARSE,
    "sem" to CompileMode.SEMANTIC,
    "semantic" to CompileMode.SEMANTIC,
)

enum class DisplayMode {
    NONE, RESULT, VERBOSE
}

val DisplayModeMap = mapOf(
    "none" to DisplayMode.NONE,
    "result" to DisplayMode.RESULT,
    "verbose" to DisplayMode.VERBOSE
)
