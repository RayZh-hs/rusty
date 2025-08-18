package rusty.lexer

import rusty.core.CompilerPointer

data class TokenBearer(val token: Token, val raw: String, val pointer: CompilerPointer) {
	val lineNumber: Int get() = pointer.line
	val columnNumber: Int get() = pointer.column
}
