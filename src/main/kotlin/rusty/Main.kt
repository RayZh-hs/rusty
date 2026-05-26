package rusty

import rusty.asm.AsmConstructor
import rusty.asm.support.AsmContext
import rusty.cli.CommandParser
import rusty.cli.CommandParserConfigEntry
import rusty.cli.Requirement
import rusty.cli.ArgType
import rusty.core.CompileError
import rusty.lexer.Lexer
import rusty.lexer.dump
import rusty.lexer.dumpScreen
import rusty.ir.IRConstructor
import rusty.ir.dump
import rusty.preprocessor.Preprocessor
import rusty.preprocessor.dump
import rusty.preprocessor.dumpScreen
import rusty.core.CompileMode
import rusty.core.CompileModeMap
import rusty.core.DisplayMode
import rusty.core.DisplayModeMap
import rusty.opt.IROptimizer
import rusty.opt.dump
import rusty.parser.Parser
import rusty.parser.dump
import rusty.parser.dumpScreen
import rusty.semantic.SemanticConstructor
import rusty.semantic.dump
import rusty.semantic.dumpScreen
import java.io.File

fun main(args: Array<String>) {
    if (OJCompileMode.isRequested(args)) {
        OJCompileMode.run()
        return
    }

    val parser = CommandParser(listOf(
        // -i /path OR --input /path
        CommandParserConfigEntry("input", Requirement.REQUIRED, ArgType.VALUE, listOf("i")),
        // -o /path OR --output /path
        CommandParserConfigEntry("output", Requirement.REQUIRED, ArgType.VALUE, listOf("o")),
        // -e mode OR --emit mode
        CommandParserConfigEntry("emit", Requirement.OPTIONAL, ArgType.VALUE, listOf("e")),
        // -v OR --verbose
        CommandParserConfigEntry("verbose", Requirement.OPTIONAL, ArgType.FLAG, listOf("v"))
    ))

    val parsed = parser.parse(args)

    // Handle Mode (--emit / -e)
    val mode: CompileMode = if (parsed.containsKey("emit")) {
        val modeStr = parsed["emit"]!!
        if (!CompileModeMap.containsKey(modeStr)) {
            throw IllegalArgumentException("Unknown emit mode: '$modeStr'")
        } else {
            CompileModeMap[modeStr]!!
        }
    } else {
        // Use default mode: fall through to largest index
        CompileMode.entries.last()
    }

    // Handle Display (--verbose / -v)
    val isVerbose = "verbose" in parsed

    val inputPath = parsed["input"]!!
    val outputPath = parsed["output"]!!

    // 0. Read from file given in -i / --input
    val rawFileLiteral = File(inputPath).readText()
    CompileError.registerSource(rawFileLiteral.split("\n"))

    // 1. Preprocessing
    val preprocessedLiteral = Preprocessor.run(rawFileLiteral)
    if (isVerbose) {
        Preprocessor.dumpScreen(preprocessedLiteral)
    }
    if (mode == CompileMode.PREPROCESS) {
        // dump into file
        Preprocessor.dump(preprocessedLiteral, outputPath)
        return
    }

    // 2. Lexical Assignment
    val lexResult = Lexer.run(preprocessedLiteral)
    if (isVerbose) {
        Lexer.dumpScreen(lexResult)
    }
    if (mode == CompileMode.LEX) {
        // dump into file
        Lexer.dump(lexResult, outputPath)
        return
    }

    // 3. Parsing
    val parseResult = Parser.run(lexResult)
    if (isVerbose) {
        Parser.dumpScreen(parseResult)
    }
    if (mode == CompileMode.PARSE) {
        Parser.dump(parseResult, outputPath)
        return
    }

    // 4. Semantic Construction
    val semanticResult = SemanticConstructor.run(parseResult, dumpToScreen = isVerbose)
    if (mode == CompileMode.SEMANTIC) {
        // dump into file
        SemanticConstructor.dump(semanticResult, outputPath)
        return
    }

    // 5. IR Generation
    val irResult = IRConstructor.run(semanticResult, dumpToScreen = isVerbose)
    if (mode == CompileMode.IR) {
        IRConstructor.dump(irResult, outputPath)
        return
    }

    // 6. IR Optimization
    val optResult = IROptimizer.run(irResult, dumpToScreen = isVerbose)
    if (mode == CompileMode.OPT) {
        IROptimizer.dump(optResult, outputPath)
        return
    }

    // 7. Assembly Generation
    val asmResult = AsmConstructor.run(AsmContext(optResult), dumpToScreen = isVerbose)
    if (mode == CompileMode.ASM) {
        File(outputPath).writeText(asmResult)
        return
    }
}
