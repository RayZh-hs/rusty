package rusty.lexer

import rusty.core.CompileError

fun TokenBearer.validate() {
    when (token) {
        Token.L_CHAR -> {
            if (!raw.matches("""'([^'\\\n\r\t]|\\['"nrt\\]|\\x[0-7][0-9a-fA-F]|\\u\{[0-9a-fA-F_]{1,6}\})'""".toRegex()))
                throw CompileError("Invalid char: $raw at ${pointer.line}:${pointer.column}")
        }
        Token.L_BYTE -> {
            if (!raw.matches("""b'((?!['\\\n\r\t])[\x00-\x7F]|\\x[0-7][0-9a-fA-F]|\\[nrt\\0'"])'""".toRegex()))
                throw CompileError("Invalid byte: $raw at ${pointer.line}:${pointer.column}")
        }
        Token.L_INTEGER -> {
            var num = raw
            setOf("u32", "i32", "usize", "isize").forEach {
                if (raw.endsWith(it)) {
                    num = num.dropLast(it.length)
                    return
                }
            }
            val reg = when {
                num.startsWith("0b") -> {
                    num = num.substring(2)
                    "[0-1]+"
                }
                num.startsWith("0o") -> {
                    num = num.substring(2)
                    "[0-7]+"
                }
                num.startsWith("0x") -> {
                    num = num.substring(2)
                    "[0-9a-fA-F]+"
                }
                else -> "[0-9]+"
            }.toRegex()
            num = num.replace("_", "")
            if (num.isEmpty())
                throw CompileError("Empty integer $raw at ${pointer.line}:${pointer.column}")
            if (!num.matches(reg))
                throw CompileError("Invalid number $raw at ${pointer.line}:${pointer.column}")
        }

        else -> {}
    }
}