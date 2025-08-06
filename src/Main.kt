import cmd.CommandParser
import cmd.CommandParserConfigEntry
import cmd.Requirement
import lexer.Lexer
import lexer.dump
import lexer.dumpScreen
import preprocessor.Preprocessor
import preprocessor.dump
import preprocessor.dumpScreen
import pub.CompileMode
import pub.CompileModeMap
import pub.DisplayMode
import pub.DisplayModeMap
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
        CompileMode.PREPROCESS
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
}