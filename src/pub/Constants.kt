package pub

enum class CompileMode {
    PREPROCESS, LEX,
}

val CompileModeMap = mapOf(
    "pp" to CompileMode.PREPROCESS,
    "preprocess" to CompileMode.PREPROCESS,
    "lex" to CompileMode.LEX,
)