package preprocessor

import com.andreapivetta.kolor.green

// This file provides dump utilities for the Preprocessor module

fun Preprocessor.Companion.dump(output: String, outputPath: String) {
    TODO("dump string to file")
}

fun Preprocessor.Companion.dumpScreen(output: String) {
    println("[rusty] Preprocessor dump:".green())
    println(output)
}