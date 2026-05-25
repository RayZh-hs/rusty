package space.norb.riscv

public data class AssemblyProgram(public val lines: List<AssemblyLine>) {
    public fun render(trailingNewline: Boolean = true): String {
        if (lines.isEmpty()) return ""

        val body = lines.joinToString(separator = "\n") { it.render() }
        return if (trailingNewline) "$body\n" else body
    }

    public fun writeTo(appendable: Appendable) {
        appendable.append(render())
    }

    public override fun toString(): String = render()
}

public sealed class AssemblyLine {
    public abstract fun render(): String
}

public data class InstructionLine(
    public val mnemonic: String,
    public val operands: List<AsmOperand> = emptyList(),
) : AssemblyLine() {
    public override fun render(): String {
        val renderedOperands = operands.joinToString(separator = ", ") { it.render() }
        return if (renderedOperands.isEmpty()) {
            "    $mnemonic"
        } else {
            "    $mnemonic $renderedOperands"
        }
    }
}

public data class DirectiveLine(
    public val name: String,
    public val operands: List<AsmOperand> = emptyList(),
) : AssemblyLine() {
    public override fun render(): String {
        val normalizedName = if (name.startsWith(".")) name else ".$name"
        val renderedOperands = operands.joinToString(separator = ", ") { it.render() }
        return if (renderedOperands.isEmpty()) {
            "    $normalizedName"
        } else {
            "    $normalizedName $renderedOperands"
        }
    }
}

public data class LabelLine(public val label: Label) : AssemblyLine() {
    public override fun render(): String = "${label.render()}:"
}

public data class CommentLine(public val text: String) : AssemblyLine() {
    public override fun render(): String = "# $text"
}

public object BlankLine : AssemblyLine() {
    public override fun render(): String = ""
}

public data class RawLine(public val text: String) : AssemblyLine() {
    public override fun render(): String = text
}
