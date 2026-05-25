package space.norb.riscv

public interface AsmOperand {
    public fun render(): String
}

public data class Immediate(public val value: Int) : AsmOperand {
    public override fun render(): String = value.toString()
}

/**
 * A raw assembly expression such as "%hi(symbol)", "1f", or "array + 4".
 */
public data class AsmExpr(public val text: String) : AsmOperand {
    init {
        require(text.isNotBlank()) { "Assembly expression must not be blank." }
    }

    public override fun render(): String = text
}

public data class Label(public val name: String) : AsmOperand {
    init {
        require(name.isNotBlank()) { "Label name must not be blank." }
        require(name.none(Char::isWhitespace)) { "Label name must not contain whitespace: $name" }
        require(!name.contains(':')) { "Label name must not contain ':': $name" }
    }

    public override fun render(): String = name
}

public data class Address(
    public val offset: AsmOperand,
    public val base: Register,
) : AsmOperand {
    public override fun render(): String = "${offset.render()}(${base.render()})"
}

public data class StringLiteral(public val value: String) : AsmOperand {
    public override fun render(): String = buildString {
        append('"')
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

public fun imm(value: Int): Immediate = Immediate(value)

public fun expr(text: String): AsmExpr = AsmExpr(text)

public fun labelRef(name: String): Label = Label(name)

public fun mem(offset: Int, base: Register): Address = Address(Immediate(offset), base)

public fun mem(offset: String, base: Register): Address = Address(AsmExpr(offset), base)

public operator fun Register.get(offset: Int): Address = mem(offset, this)

public operator fun Register.get(offset: String): Address = mem(offset, this)
