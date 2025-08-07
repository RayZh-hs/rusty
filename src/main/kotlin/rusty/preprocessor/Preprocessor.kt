package rusty.preprocessor

import rusty.core.MarkedString
import rusty.core.MarkedStringBuilder

// The preprocessor is responsible for removing comments and standardizing string literals
// The result will be a one-liner for the inputted text literal
class Preprocessor {
    companion object {
        fun run(rawText: String): MarkedString {
            val text = MarkedStringBuilder()

            var nestDepth = 0
            var inLineComment = false
            var inString = false
            var inChar = false
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
                                '\\' -> isEscaped = true // Mark the next character as escaped.
                                '"' -> inString = false  // End of the string literal
                            }
                        }
                        idx++
                        continue
                    }
                    inChar -> {
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
                                '\\' -> isEscaped = true // Mark the next character as escaped.
                                '\'' -> inChar = false  // End of the string literal
                            }
                        }
                        idx++
                        continue
                    }
                }

                val (gotoCur, newlineCount) = skipToOutsideOfRawLiterals(rawText, idx)
                lineNumber += newlineCount
                if (gotoCur != idx) {
                    // push the segment into the string builder
                    text.append(rawText.substring(idx, gotoCur), lineNumber)
                    idx = gotoCur
                    continue
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
                    // Char
                    c == '\'' -> {
                        inChar = true
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

        // cursor, newline count
        private fun skipToOutsideOfRawLiterals(input: String, cur: Int): Pair<Int, Int> {
            val c = input.getOrNull(cur)
            val nc = input.getOrNull(cur + 1)

            val startOfHashes: Int
            when {
                // prefix: br, cr
                (c == 'b' || c == 'c') && nc == 'r' -> {
                    startOfHashes = cur + 2
                }
                // prefix: r
                c == 'r' -> {
                    startOfHashes = cur + 1
                }
                else -> return Pair(cur, 0)
            }

            var postHashesIndex = startOfHashes
            while (postHashesIndex < input.length && input[postHashesIndex] == '#') {
                postHashesIndex++
            }
            val hashCount = postHashesIndex - startOfHashes

            if (input.getOrNull(postHashesIndex) != '"') {
                return Pair(cur, 0)
            }

            val closingDelimiter = "\"" + "#".repeat(hashCount)
            val closingIndex = input.indexOf(closingDelimiter, startIndex = postHashesIndex + 1)
            if (closingIndex == -1) {
                return Pair(cur, 0)
            }
            val newlineCount = input.substring(postHashesIndex + 1, closingIndex).count { it == '\n' }

            // Return the index right after the end of the closing delimiter.
            return Pair(closingIndex + closingDelimiter.length, newlineCount)
        }
    }
}