package rusty

import rusty.cli.CommandParser
import rusty.cli.CommandParserConfigEntry
import rusty.cli.Requirement
import rusty.core.CompileError
import rusty.lexer.Lexer
import rusty.lexer.dump
import rusty.lexer.dumpScreen
import rusty.preprocessor.Preprocessor
import rusty.preprocessor.dump
import rusty.preprocessor.dumpScreen
import rusty.core.CompileMode
import rusty.core.CompileModeMap
import rusty.core.DisplayMode
import rusty.core.DisplayModeMap
import rusty.parser.Parser
import rusty.parser.dump
import rusty.parser.dumpScreen
import rusty.semantic.SemanticConstructor
import rusty.semantic.dump
import rusty.semantic.dumpScreen
import java.io.File

fun main(args: Array<String>) {
    val parser = CommandParser(listOf(
        CommandParserConfigEntry("i", Requirement.REQUIRED),    // input file path
        CommandParserConfigEntry("m", Requirement.OPTIONAL),    // mode of compilation
        CommandParserConfigEntry("o", Requirement.REQUIRED),    // output file path
        CommandParserConfigEntry("s", Requirement.OPTIONAL),    // show options
    ))
    val parsed = parser.parse(args)

    val mode: CompileMode = if (parsed.containsKey("m")) {
        if (!CompileModeMap.containsKey(parsed["m"])) {
            throw IllegalArgumentException("Unknown mode: '${parsed["m"]}'")
        } else {
            CompileModeMap[parsed["m"]]!!
        }
    }
    else {
        // Use default mode
        CompileMode.PARSE
    }
    val displayMode: DisplayMode = if (parsed.containsKey("s")) {
        if (!DisplayModeMap.containsKey(parsed["s"])) {
            throw IllegalArgumentException("Unknown display mode: '${parsed["s"]}'")
        } else {
            DisplayModeMap[parsed["s"]]!!
        }
    } else {
        DisplayMode.RESULT
    }
    val inputPath = parsed["i"]!!
    val outputPath = parsed["o"]!!

    // 0. Read from file given in -i
    val rawFileLiteral = File(inputPath).readText()
    CompileError.registerSource(rawFileLiteral.split("\n"))

    // 1. Preprocessing
    val preprocessedLiteral = Preprocessor.run(rawFileLiteral)
    if (displayMode == DisplayMode.VERBOSE) {
        Preprocessor.dumpScreen(preprocessedLiteral)
    }
    if (mode == CompileMode.PREPROCESS) {
        // dump into file
        Preprocessor.dump(preprocessedLiteral, outputPath)
        return
    }

    // 2. Lexical Assignment
    val lexResult = Lexer.run(preprocessedLiteral)
    if (displayMode == DisplayMode.VERBOSE) {
        Lexer.dumpScreen(lexResult)
    }
    if (mode == CompileMode.LEX) {
        // dump into file
        Lexer.dump(lexResult, outputPath)
        return
    }

    // 3. Parsing
    val parseResult = Parser.run(lexResult)
    if (displayMode == DisplayMode.VERBOSE) {
        Parser.dumpScreen(parseResult)
    }
    if (mode == CompileMode.PARSE) {
        Parser.dump(parseResult, outputPath)
        return
    }

    // 4. Semantic Construction
    val semanticResult = when (displayMode) {
        DisplayMode.VERBOSE -> SemanticConstructor.run(parseResult, dumpToScreen = true)
        else -> SemanticConstructor.run(parseResult, dumpToScreen = false)
    }
    if (mode == CompileMode.SEMANTIC) {
        // dump into file
        SemanticConstructor.dump(semanticResult, outputPath)
        return
    }
}
