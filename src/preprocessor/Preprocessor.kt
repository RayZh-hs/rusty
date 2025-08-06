package preprocessor

import pub.MarkedString
import pub.MarkedStringBuilder

// The preprocessor is responsible for removing comments and standardizing string literals
// The result will be a one-liner for the inputted text literal
class Preprocessor {
    companion object {
        fun run(rawText: String): MarkedString {
            val text = MarkedStringBuilder()

            var nestDepth = 0
            var inLineComment = false
            var inString = false
            var inByteString = false
            var isEscaped = false
            var lineNumber = 1

            var idx = 0
            while (idx < rawText.length) {
                val c = rawText[idx]
                val nc: Char? = if (idx + 1 < rawText.length) rawText[idx + 1] else null

                if (c == '\n')
                    ++lineNumber

                // Special positions
                when {
                    inLineComment -> {
                        if (c == '\n') {
                            text.append(' ', lineNumber)
                            inLineComment = false
                        }
                        ++idx
                        continue
                    }
                    (nestDepth > 0) -> {
                        if (c == '/' && nc == '*') {
                            ++nestDepth
                            ++idx
                        } else if (c == '*' && nc == '/') {
                            --nestDepth
                            ++idx
                        }
                        ++idx
                        continue
                    }
                    inString -> {
                        if (c == '\\' && nc == '\n') {
                            // skip the \n
                            idx += 2
                            continue
                        }
                        text.append(
                            when (c) {
                                '\n' -> "\\n"   // change \n to \\n literal to make it a one-liner
                                else -> c.toString()
                            }, lineNumber
                        )
                        if (isEscaped) {
                            isEscaped = false
                        } else {
                            when (c) {
                                '\\' -> isEscaped = true // Mark the next character as escaped. [4]
                                '"' -> inString = false  // End of the string literal
                            }
                        }
                        idx++
                        continue
                    }
                    inByteString -> {
                        if (c == '\\' && nc == '\n') {
                            // skip the \n
                            idx += 2
                            continue
                        }
                        text.append(
                            when (c) {
                                '\n' -> "\\n"   // change \n to \\n literal to make it a one-liner
                                else -> c.toString()
                            }, lineNumber
                        )
                        if (isEscaped) {
                            isEscaped = false
                        } else {
                            when (c) {
                                '\\' -> isEscaped = true // Mark the next character as escaped. [4]
                                '\'' -> inByteString = false  // End of the string literal
                            }
                        }
                        idx++
                        continue
                    }
                }

                // Default field
                when {
                    // Line comment
                    c == '/' && nc == '/' -> {
                        inLineComment = true
                        idx++ // Consume the second '/'
                    }
                    // Block comment
                    c == '/' && nc == '*' -> {
                        nestDepth++
                        idx++ // Consume the '*'
                    }
                    // String
                    c == '"' -> {
                        inString = true
                        text.append(c, lineNumber)
                    }
                    // Byte string
                    c == '\'' -> {
                        inByteString = true
                        text.append(c, lineNumber)
                    }
                    // Replace newline with space
                    c == '\n' || c == '\r' -> {
                        // Concentrate multiple newlines
                        if (text.content.isNotEmpty() && text.content.last() != ' ') {
                            text.append(' ', lineNumber)
                        }
                    }
                    c == ' ' -> {
                        // Concentrate multiple spaces
                        if (text.content.isNotEmpty() && text.content.last() != ' ') {
                            text.append(c, lineNumber)
                        }
                    }
                    // Append any other character faithfully
                    else -> {
                        text.append(c, lineNumber)
                    }
                }
                ++idx
            }
            return text.toMarkedString()
        }
    }
}