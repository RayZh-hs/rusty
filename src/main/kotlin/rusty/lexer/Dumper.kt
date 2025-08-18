package rusty.lexer

import com.andreapivetta.kolor.*
import java.io.File

// This file provides dump utilities for the Lexer module

fun Lexer.Companion.dump(tokens: MutableList<TokenBearer>, outputPath: String) {
    val file = File(outputPath)
    val output = buildString {
        appendLine("Total tokens: ${tokens.size}")
        appendLine()
        tokens.forEachIndexed { index, tokenBearer ->
            appendLine("[$index] ${formatTokenForFile(tokenBearer)}")
        }
    }
    file.writeText(output)
}

fun Lexer.Companion.dumpScreen(tokens: MutableList<TokenBearer>) {
    println("[rusty] Lexer dump:".green())
    tokens.forEach { tokenBearer: TokenBearer -> print(formatTokenForScreenConcat(tokenBearer)) }
    println()
    println("Total tokens: ${tokens.size}".cyan())
    tokens.forEachIndexed { index, tokenBearer ->
        println("[$index] ${formatTokenForScreen(tokenBearer)}")
    }
    println()
}

private fun formatTokenForFile(tokenBearer: TokenBearer): String {
    return "Token(${tokenBearer.token.getType()}: ${tokenBearer.token}) " +
           "raw='${tokenBearer.raw}' line=${tokenBearer.lineNumber} col=${tokenBearer.columnNumber}"
}

private fun formatTokenForScreen(tokenBearer: TokenBearer): String {
    val tokenTypeStr = when (tokenBearer.token.getType()) {
        TokenType.IDENTIFIER -> "IDENTIFIER".blue()
        TokenType.OPERATOR -> "OPERATOR".yellow()
        TokenType.KEYWORD -> "KEYWORD".magenta()
        TokenType.RESERVED_KEYWORD -> "RESERVED".red()
        TokenType.LITERAL -> "LITERAL".green()
        TokenType.ERROR -> "ERROR".red()
    }
    
    val tokenName = tokenBearer.token.toString().lightGray()
    val rawValue = "'${tokenBearer.raw}'".cyan()
    val lineInfo = "line=${tokenBearer.lineNumber} col=${tokenBearer.columnNumber}".lightGray()
    
    return "Token($tokenTypeStr: $tokenName) raw=$rawValue $lineInfo"
}

private fun formatTokenForScreenConcat(tokenBearer: TokenBearer): String {
    val rawValue = when (tokenBearer.token.getType()) {
        TokenType.IDENTIFIER -> tokenBearer.raw.blue()
        TokenType.OPERATOR -> tokenBearer.raw.yellow()
        TokenType.KEYWORD -> tokenBearer.raw.magenta()
        TokenType.RESERVED_KEYWORD -> tokenBearer.raw.red()
        TokenType.LITERAL -> tokenBearer.raw.green()
        TokenType.ERROR -> tokenBearer.raw.red()
    }

    return "$rawValue "
}