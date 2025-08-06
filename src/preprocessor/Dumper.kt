package preprocessor

import com.andreapivetta.kolor.green
import pub.MarkedString
import java.io.File

// This file provides dump utilities for the Preprocessor module

fun Preprocessor.Companion.dump(output: MarkedString, outputPath: String) {
    val file = File(outputPath)
    file.writeText(output.text)
}

fun Preprocessor.Companion.dumpScreen(output: MarkedString) {
    println("[rusty] Preprocessor dump:".green())
    println(output.text)
    println()
}