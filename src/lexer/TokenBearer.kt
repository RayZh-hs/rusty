package lexer

data class TokenBearer(val token: Token, val raw: String, val lineNumber: Int)
