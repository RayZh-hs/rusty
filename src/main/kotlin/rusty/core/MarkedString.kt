package rusty.core

data class MarkedString(val text: String, val marks: List<Int>) {

    init {
        // Ensure that the size of the string and the marks list are identical.
        require(text.length == marks.size) {
            "The length of the string (${text.length}) must be equal to the size of the marks list (${marks.size})."
        }
    }

    fun getChar(index: Int): Char = text[index]
    fun getMark(index: Int): Int = marks[index]
    val length: Int
        get() = text.length
}

class MarkedStringBuilder {

    val content = StringBuilder()
    private val marksList = mutableListOf<Int>()

    fun append(char: Char, mark: Int): MarkedStringBuilder {
        content.append(char)
        marksList.add(mark)
        return this
    }

    // uniform mark
    fun append(text: String, mark: Int): MarkedStringBuilder {
        text.forEach { char ->
            append(char, mark)
        }
        return this
    }

    fun append(markedString: MarkedString): MarkedStringBuilder {
        content.append(markedString.text)
        marksList.addAll(markedString.marks)
        return this
    }

    fun toMarkedString(): MarkedString {
        return MarkedString(content.toString(), marksList.toList())
    }

    override fun toString(): String {
        return content.toString()
    }
}
