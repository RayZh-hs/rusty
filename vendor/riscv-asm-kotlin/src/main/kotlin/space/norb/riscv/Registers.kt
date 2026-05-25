package space.norb.riscv

/**
 * A RISC-V integer register.
 *
 * Use the ABI aliases such as [a0], [sp], and [ra] for readable assembly, or [x]
 * when a numeric register is more appropriate.
 */
public data class Register(
    public val number: Int,
    public val abiName: String,
) : AsmOperand {
    init {
        require(number in 0..31) { "RISC-V register number must be in 0..31, got $number." }
        require(abiName.isNotBlank()) { "Register name must not be blank." }
    }

    public override fun render(): String = abiName

    public override fun toString(): String = render()
}

private val registersByNumber: List<Register> = listOf(
    Register(0, "zero"),
    Register(1, "ra"),
    Register(2, "sp"),
    Register(3, "gp"),
    Register(4, "tp"),
    Register(5, "t0"),
    Register(6, "t1"),
    Register(7, "t2"),
    Register(8, "s0"),
    Register(9, "s1"),
    Register(10, "a0"),
    Register(11, "a1"),
    Register(12, "a2"),
    Register(13, "a3"),
    Register(14, "a4"),
    Register(15, "a5"),
    Register(16, "a6"),
    Register(17, "a7"),
    Register(18, "s2"),
    Register(19, "s3"),
    Register(20, "s4"),
    Register(21, "s5"),
    Register(22, "s6"),
    Register(23, "s7"),
    Register(24, "s8"),
    Register(25, "s9"),
    Register(26, "s10"),
    Register(27, "s11"),
    Register(28, "t3"),
    Register(29, "t4"),
    Register(30, "t5"),
    Register(31, "t6"),
)

public fun x(number: Int): Register {
    require(number in 0..31) { "RISC-V register number must be in 0..31, got $number." }
    return registersByNumber[number]
}

public val zero: Register = x(0)
public val ra: Register = x(1)
public val sp: Register = x(2)
public val gp: Register = x(3)
public val tp: Register = x(4)
public val t0: Register = x(5)
public val t1: Register = x(6)
public val t2: Register = x(7)
public val s0: Register = x(8)
public val fp: Register = s0
public val s1: Register = x(9)
public val a0: Register = x(10)
public val a1: Register = x(11)
public val a2: Register = x(12)
public val a3: Register = x(13)
public val a4: Register = x(14)
public val a5: Register = x(15)
public val a6: Register = x(16)
public val a7: Register = x(17)
public val s2: Register = x(18)
public val s3: Register = x(19)
public val s4: Register = x(20)
public val s5: Register = x(21)
public val s6: Register = x(22)
public val s7: Register = x(23)
public val s8: Register = x(24)
public val s9: Register = x(25)
public val s10: Register = x(26)
public val s11: Register = x(27)
public val t3: Register = x(28)
public val t4: Register = x(29)
public val t5: Register = x(30)
public val t6: Register = x(31)
