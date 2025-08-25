package rusty.semantic

import com.andreapivetta.kolor.green
import java.io.File

// This file provides dump utilities for the SemanticConstructor module

fun SemanticConstructor.Companion.dump(output: OutputType, outputPath: String) {
    val file = File(outputPath)
    file.writeText(output.toString(withColor = false))
}

fun SemanticConstructor.Companion.dumpScreen(output: OutputType) {
    println("[rusty] Preprocessor dump:".green())
    println(output.toString(withColor = true))
    println()
}