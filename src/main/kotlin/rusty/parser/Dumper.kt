package rusty.parser

import com.andreapivetta.kolor.green
import rusty.core.MarkedString

// This file provides dump utilities for the Parser module

fun Parser.Companion.dump(output: ASTTree, outputPath: String) {
}

fun Parser.Companion.dumpScreen(output: ASTTree) {
    println("[rusty] Parser dump:".green())
    println()
}