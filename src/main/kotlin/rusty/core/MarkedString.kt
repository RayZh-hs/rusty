package rusty.core

/**
 * MarkedString now associates every character with its original CompilerPointer (line & column).
 */
data class MarkedString(val text: String, val marks: List<CompilerPointer>) {

    init {
        require(text.length == marks.size) {
            "The length of the string (${text.length}) must be equal to the size of the marks list (${marks.size})."
        }
    }

    fun getChar(index: Int): Char = text[index]
    fun getMark(index: Int): CompilerPointer = marks[index]
    val length: Int get() = text.length
}

class MarkedStringBuilder {
    val content = StringBuilder()
    private val marksList = mutableListOf<CompilerPointer>()

    fun append(char: Char, mark: CompilerPointer): MarkedStringBuilder {
        content.append(char)
        marksList.add(mark)
        return this
    }

    fun append(text: String, mark: CompilerPointer): MarkedStringBuilder {
        text.forEach { char -> append(char, mark) }
        return this
    }

    fun append(markedString: MarkedString): MarkedStringBuilder {
        content.append(markedString.text)
        marksList.addAll(markedString.marks)
        return this
    }

    fun toMarkedString(): MarkedString = MarkedString(content.toString(), marksList.toList())

    override fun toString(): String = content.toString()
}
