import cmd.CommandParser
import cmd.CommandParserConfigEntry
import cmd.Requirement
import preprocessor.Preprocessor
import preprocessor.dump
import preprocessor.dumpScreen
import pub.CompileMode
import pub.CompileModeMap
import java.io.File

fun main(args: Array<String>) {
    val parser = CommandParser(listOf(
        CommandParserConfigEntry("i", Requirement.REQUIRED),    // input file path
        CommandParserConfigEntry("m", Requirement.OPTIONAL),    // mode of compilation
        CommandParserConfigEntry("o", Requirement.REQUIRED)     // output file path
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
    val inputPath = parsed["i"]!!
    val outputPath = parsed["o"]!!

    // 0. Read from file given in -i
    val rawFileLiteral = File(inputPath).readText()

    // 1. Preprocessing
    val preprocessedLiteral = Preprocessor.run(rawFileLiteral)
    if (mode == CompileMode.PREPROCESS) {
        // dump to screen
        Preprocessor.dumpScreen(preprocessedLiteral)
        // dump into file
        Preprocessor.dump(preprocessedLiteral, outputPath)
    }
}