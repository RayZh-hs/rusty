package rusty.core.utils

fun Char.isWord(): Boolean = this == '_' || this.isDigit() || this.isLetter()
fun Char.isWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
