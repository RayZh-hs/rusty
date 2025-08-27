package rusty.parser.nodes.utils

import rusty.core.CompileError
import rusty.lexer.Token
import rusty.lexer.TokenBearer
import rusty.parser.nodes.ExpressionNode.WithoutBlockExpressionNode.LiteralExpressionNode

fun literalFromInteger(tokenBearer: TokenBearer): LiteralExpressionNode {
    val rawString: String = tokenBearer.raw
    when (tokenBearer.token) {
        Token.L_INTEGER -> {
            var (numericPart, suffix) = rawString.findSuffix(listOf("u32", "i32", "usize", "isize"))

            // Remove underscores
            numericPart = numericPart.replace("_", "")

            val radix = when {
                numericPart.startsWith("0x") -> 16
                numericPart.startsWith("0o") -> 8
                numericPart.startsWith("0b") -> 2
                else -> 10
            }

            if (radix != 10) {
                numericPart = numericPart.substring(2)
            }

            return when (suffix) {
                "u32"   -> LiteralExpressionNode.U32LiteralNode(numericPart.toUInt(radix), tokenBearer.pointer)
                "usize" -> LiteralExpressionNode.USizeLiteralNode(numericPart.toUInt(radix), tokenBearer.pointer)
                "i32"   -> LiteralExpressionNode.I32LiteralNode(numericPart.toInt(radix), tokenBearer.pointer)
                "isize" -> LiteralExpressionNode.ISizeLiteralNode(numericPart.toInt(radix), tokenBearer.pointer)
                else -> {
                    LiteralExpressionNode.AnyIntegerLiteralNode(numericPart.toInt(radix), tokenBearer.pointer)
                }
            }
        }
        else -> throw AssertionError("Expected an integer literal token, but found ${tokenBearer.token}")
    }
}

fun literalFromString(tokenBearer: TokenBearer): LiteralExpressionNode {
    val rawString: String = tokenBearer.raw
    when (tokenBearer.token) {
        Token.L_STRING -> {
            val content = rawString.substring(1, rawString.length - 1)
            return LiteralExpressionNode.StringLiteralNode(unescapeString(content), tokenBearer.pointer)
        }
        Token.L_RAW_STRING -> {
            val firstQuote = rawString.indexOf('"')
            val hashes = rawString.substring(1, firstQuote)
            val content = rawString.substring(firstQuote + 1, rawString.length - 1 - hashes.length)
            return LiteralExpressionNode.StringLiteralNode(content, tokenBearer.pointer)
        }
        Token.L_C_STRING -> {
            val content = rawString.substring(2, rawString.length - 1)
            return LiteralExpressionNode.CStringLiteralNode(unescapeString(content), tokenBearer.pointer)
        }
        Token.L_RAW_C_STRING -> {
            val firstQuote = rawString.indexOf('"')
            val hashes = rawString.substring(2, firstQuote)
            val content = rawString.substring(firstQuote + 1, rawString.length - 1 - hashes.length)
            return LiteralExpressionNode.CStringLiteralNode(content, tokenBearer.pointer)
        }
        else -> throw AssertionError("Expected a string literal token, but found ${tokenBearer.token}")
    }
}

fun literalFromChar(tokenBearer: TokenBearer): LiteralExpressionNode {
    val rawString: String = tokenBearer.raw
    when (tokenBearer.token) {
        Token.L_CHAR -> {
            val content = rawString.substring(1, rawString.length - 1)
            val unescaped = unescapeString(content)
            if (unescaped.length != 1) {
                throw AssertionError("Character literal must be a single character, but found '$unescaped'")
            }
            return LiteralExpressionNode.CharLiteralNode(unescaped[0], tokenBearer.pointer)
        }
        else -> throw AssertionError("Expected a char literal token, but found ${tokenBearer.token}")
    }
}

fun literalFromBoolean(tokenBearer: TokenBearer): LiteralExpressionNode {
    return when (tokenBearer.token) {
        Token.K_TRUE -> LiteralExpressionNode.BoolLiteralNode(true, tokenBearer.pointer)
        Token.K_FALSE -> LiteralExpressionNode.BoolLiteralNode(false, tokenBearer.pointer)
        else -> throw AssertionError("Expected a boolean literal token, but found ${tokenBearer.token}")
    }
}

// A helper function to find a suffix from a list of possible suffixes.
// Returns a pair of the string without the suffix and the suffix found (or null).
private fun String.findSuffix(suffixes: List<String>): Pair<String, String?> {
    for (suffix in suffixes) {
        if (this.endsWith(suffix)) {
            return Pair(this.removeSuffix(suffix), suffix)
        }
    }
    return Pair(this, null)
}


// A helper function to unescape a string based on Rust's escape sequences.
private fun unescapeString(str: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < str.length) {
        if (str[i] == '\\') {
            i++
            when (str[i]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                '\\' -> sb.append('\\')
                '0' -> sb.append('\u0000')
                '\'' -> sb.append('\'')
                '"' -> sb.append('"')
                'x' -> {
                    val hex = str.substring(i + 1, i + 3)
                    sb.append(hex.toInt(16).toChar())
                    i += 2
                }
                'u' -> {
                    val closingBrace = str.indexOf('}', i)
                    val hex = str.substring(i + 2, closingBrace)
                    sb.append(hex.toInt(16).toChar())
                    i = closingBrace
                }
                else -> {
                    throw CompileError("Illegal escape sequence: \\${str[i]}")
                }
            }
        } else {
            sb.append(str[i])
        }
        i++
    }
    return sb.toString()
}