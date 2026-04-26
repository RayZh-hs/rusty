package rusty.core

enum class CompileMode {
    PREPROCESS, LEX, PARSE, SEMANTIC, IR, OPT, ASM,
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
    "opt" to CompileMode.OPT,
    "optimize" to CompileMode.OPT,
    "optimization" to CompileMode.OPT,
    "asm" to CompileMode.ASM,
    "s" to CompileMode.ASM,
    "rv32im" to CompileMode.ASM,
    "riscv" to CompileMode.ASM,
)

enum class DisplayMode {
    NONE, RESULT, VERBOSE
}

val DisplayModeMap = mapOf(
    "none" to DisplayMode.NONE,
    "result" to DisplayMode.RESULT,
    "verbose" to DisplayMode.VERBOSE
)
