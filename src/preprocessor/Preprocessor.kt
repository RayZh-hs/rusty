package preprocessor

// The preprocessor is responsible for removing comments and standardizing string literals
// The result will be a one-liner for the inputted text literal
class Preprocessor {
    companion object {
        fun run(rawText: String): String {
            val text: StringBuilder = StringBuilder()

            var nestDepth = 0
            var inLineComment = false
            var inString = false
            var inByteString = false
            var isEscaped = false

            var idx = 0
            while (idx < rawText.length) {
                val c = rawText[idx]
                val nc: Char? = if (idx + 1 < rawText.length) rawText[idx + 1] else null

                // Special positions
                when {
                    inLineComment -> {
                        if (c == '\n') {
                            text.append(' ')
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
                                else -> c
                            }
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
                                else -> c
                            }
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
                        text.append(c)
                    }
                    // Byte string
                    c == '\'' -> {
                        inByteString = true
                        text.append(c)
                    }
                    // Replace newline with space
                    c == '\n' || c == '\r' -> {
                        // Concentrate multiple newlines
                        if (text.isNotEmpty() && text.last() != ' ') {
                            text.append(' ')
                        }
                    }
                    c == ' ' -> {
                        // Concentrate multiple spaces
                        if (text.isNotEmpty() && text.last() != ' ') {
                            text.append(c)
                        }
                    }
                    // Append any other character faithfully
                    else -> {
                        text.append(c)
                    }
                }
                ++idx
            }
            return text.toString().trim()
        }
    }
}