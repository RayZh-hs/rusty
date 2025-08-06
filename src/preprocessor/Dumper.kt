package preprocessor

import com.andreapivetta.kolor.green
import java.io.File

// This file provides dump utilities for the Preprocessor module

fun Preprocessor.Companion.dump(output: String, outputPath: String) {
    val file = File(outputPath)
    file.writeText(output)
}

fun Preprocessor.Companion.dumpScreen(output: String) {
    println("[rusty] Preprocessor dump:".green())
    println(output)
}