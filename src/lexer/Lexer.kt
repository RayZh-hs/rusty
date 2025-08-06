package lexer

import pub.MarkedString
import pub.isWord
import pub.CompileError

class Lexer {

    enum class TokenPeekClass {
        STRING, CHAR, BYTE, BYTE_STRING, C_STRING, NUMERIC, OPERATOR, KEY_OR_ID
    }

    companion object {
        fun run(input: MarkedString): MutableList<TokenBearer> {
            val tokens: MutableList<TokenBearer> = mutableListOf()

            val string = input.text

            var cur = getNextNonWhitespace(string, 0)
            while (cur < input.length) {
                val (token, endpoint) = nextTokenBearer(string, cur, input.getMark(cur))
                tokens.addLast(token)
                cur = getNextNonWhitespace(string, endpoint + 1)
            }
            return tokens
        }

        private fun peekClass(input: String, cur: Int): TokenPeekClass {
            when (input[cur]) {
                // Numeric Components
                in '0'..'9' -> return TokenPeekClass.NUMERIC
                '"' -> return TokenPeekClass.STRING
                '\'' -> return TokenPeekClass.CHAR
                'b' -> {
                    // look for byte pattern
                    if (cur + 1 >= input.length) {
                        return TokenPeekClass.KEY_OR_ID
                    }
                    val nc = input[cur + 1]
                    when (nc) {
                        '\'' -> return TokenPeekClass.BYTE
                        '\"' -> return TokenPeekClass.BYTE_STRING
                    }
                    return TokenPeekClass.KEY_OR_ID
                }

                'c' -> {
                    // look for c pattern
                    if (cur + 1 >= input.length) {
                        return TokenPeekClass.KEY_OR_ID
                    }
                    val nc = input[cur + 1]
                    when (nc) {
                        '\"' -> return TokenPeekClass.C_STRING
                    }
                    return TokenPeekClass.KEY_OR_ID
                }

                in 'A'..'Z', in 'a'..'z', '_' -> {
                    return TokenPeekClass.KEY_OR_ID
                }

                else -> return TokenPeekClass.OPERATOR
            }
        }

        private fun nextTokenBearer(input: String, cur: Int, lineNumber: Int): Pair<TokenBearer, Int> {
            val cls = peekClass(input, cur)
            when (cls) {
                // Word boundary lookup
                TokenPeekClass.NUMERIC, TokenPeekClass.KEY_OR_ID -> {
                    // find the last position that is not a word-char
                    var i = cur + 1
                    while (i < input.length && input[i].isWord())
                        ++i
                    --i     // this points to the endpoint
                    val substr = input.slice(cur..i)
                    return Pair(
                        when (cls) {
                            TokenPeekClass.NUMERIC -> TokenBearer(Token.L_INTEGER, substr, lineNumber)
                            else -> {
                                val tokenTypeLookup = tokenFromLiteral(substr)
                                TokenBearer(
                                    if (tokenTypeLookup == Token.E_ERROR) Token.I_IDENTIFIER else tokenTypeLookup,
                                    substr,
                                    lineNumber
                                )
                            }
                        }, i
                    )
                }
                // Escaped string lookup
                TokenPeekClass.STRING, TokenPeekClass.BYTE, TokenPeekClass.CHAR,
                TokenPeekClass.C_STRING, TokenPeekClass.BYTE_STRING -> {
                    val i = cur + when(cls) {
                        TokenPeekClass.STRING, TokenPeekClass.CHAR -> 1
                        else -> 2
                    }
                    val terminator = when(cls) {
                        TokenPeekClass.CHAR, TokenPeekClass.BYTE -> '\''
                        else -> '\"'
                    }
                    var j = i
                    while (j < input.length) {
                        if (input[j] == '\\') { // directly skip the escaped char
                            j += 2
                            continue
                        }
                        if (input[j] == terminator) {
                            // Found the end of the literal
                            val endpoint = j
                            val substr = input.slice(cur..endpoint)
                            val tokenType = when (cls) {
                                TokenPeekClass.STRING -> Token.L_STRING
                                TokenPeekClass.CHAR -> Token.L_CHAR
                                TokenPeekClass.BYTE -> Token.L_BYTE
                                TokenPeekClass.BYTE_STRING -> Token.L_BYTE_STRING
                                TokenPeekClass.C_STRING -> Token.L_C_STRING
                                else -> throw IllegalStateException("Invalid token class in string processing")
                            }
                            return Pair(TokenBearer(tokenType, substr, lineNumber), endpoint)
                        }
                        j++
                    }
                    throw CompileError("Unterminated literal on or after line: $lineNumber")
                }
                // Operator lookup
                TokenPeekClass.OPERATOR -> {
                    var i = cur + 2
                    while (i >= cur) {
                        if (i < input.length) {
                            val substr = input.substring(cur, i + 1)
                            val type = tokenFromLiteral(substr)
                            if (type != Token.E_ERROR)
                                return Pair(TokenBearer(type, substr, lineNumber), i)
                        }
                        --i
                    }
                    throw CompileError("Unrecognized operator starting with ${input[cur]} on or after line: $lineNumber")
                }
            }
        }

        private fun getNextNonWhitespace(input: String, cur: Int): Int {
            var i = cur
            while (i < input.length && input[i].isWhitespace()) {
                ++i
            }
            return i
        }
    }
}