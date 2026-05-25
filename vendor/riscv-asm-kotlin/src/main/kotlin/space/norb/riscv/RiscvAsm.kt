package space.norb.riscv

public fun riscv(
    target: RiscvTarget = RiscvTarget.RV32IM,
    block: RiscvAsm.() -> Unit,
): AssemblyProgram {
    val asm = RiscvAsm(target)
    asm.block()
    return asm.build()
}

public class RiscvAsm(public val target: RiscvTarget = RiscvTarget.RV32IM) {
    private val lines: MutableList<AssemblyLine> = mutableListOf()

    public fun build(): AssemblyProgram = AssemblyProgram(lines.toList())

    public fun emit(mnemonic: String, vararg operands: AsmOperand) {
        require(mnemonic.isNotBlank()) { "Instruction mnemonic must not be blank." }
        lines += InstructionLine(mnemonic, operands.toList())
    }

    public fun directive(name: String, vararg operands: AsmOperand) {
        require(name.isNotBlank()) { "Directive name must not be blank." }
        lines += DirectiveLine(name, operands.toList())
    }

    public fun raw(line: String) {
        lines += RawLine(line)
    }

    public operator fun String.unaryPlus() {
        raw(this)
    }

    public fun comment(text: String) {
        lines += CommentLine(text)
    }

    public fun blank() {
        lines += BlankLine
    }

    public fun label(name: String): Label {
        val label = Label(name)
        label(label)
        return label
    }

    public fun label(label: Label) {
        lines += LabelLine(label)
    }

    public fun section(name: String) {
        directive(".section", expr(name))
    }

    public fun text() {
        section(".text")
    }

    public fun data() {
        section(".data")
    }

    public fun bss() {
        section(".bss")
    }

    public fun rodata() {
        section(".rodata")
    }

    public fun sdata() {
        section(".sdata")
    }

    public fun sbss() {
        section(".sbss")
    }

    public fun global(symbol: String) {
        directive(".globl", expr(symbol))
    }

    public fun globl(symbol: String) {
        global(symbol)
    }

    public fun type(symbol: String, type: String) {
        directive(".type", expr(symbol), expr(type))
    }

    public fun size(symbol: String, expression: String) {
        directive(".size", expr(symbol), expr(expression))
    }

    public fun align(power: Int) {
        require(power >= 0) { "Alignment power must be non-negative, got $power." }
        directive(".align", imm(power))
    }

    public fun balign(bytes: Int) {
        require(bytes >= 0) { "Byte alignment must be non-negative, got $bytes." }
        directive(".balign", imm(bytes))
    }

    public fun p2align(power: Int) {
        require(power >= 0) { "Power-of-two alignment must be non-negative, got $power." }
        directive(".p2align", imm(power))
    }

    public fun byte(vararg values: Int) {
        directive(".byte", *values.map(::imm).toTypedArray())
    }

    public fun half(vararg values: Int) {
        directive(".half", *values.map(::imm).toTypedArray())
    }

    public fun word(vararg values: Int) {
        directive(".word", *values.map(::imm).toTypedArray())
    }

    public fun word(vararg values: AsmOperand) {
        directive(".word", *values)
    }

    public fun ascii(value: String) {
        directive(".ascii", StringLiteral(value))
    }

    public fun asciz(value: String) {
        directive(".asciz", StringLiteral(value))
    }

    public fun string(value: String) {
        asciz(value)
    }

    public fun space(bytes: Int) {
        require(bytes >= 0) { "Space directive byte count must be non-negative, got $bytes." }
        directive(".space", imm(bytes))
    }

    public fun zero(bytes: Int) {
        require(bytes >= 0) { "Zero directive byte count must be non-negative, got $bytes." }
        directive(".zero", imm(bytes))
    }

    public fun equ(symbol: String, value: Int) {
        directive(".equ", expr(symbol), imm(value))
    }

    public fun equ(symbol: String, value: String) {
        directive(".equ", expr(symbol), expr(value))
    }

    public fun option(option: String) {
        directive(".option", expr(option))
    }

    public fun attribute(name: String, value: String) {
        directive(".attribute", expr(name), expr(value))
    }

    public fun lui(rd: Register, imm: Int) {
        requireI()
        emit("lui", rd, Immediate(imm))
    }

    public fun lui(rd: Register, imm: AsmOperand) {
        requireI()
        emit("lui", rd, imm)
    }

    public fun auipc(rd: Register, imm: Int) {
        requireI()
        emit("auipc", rd, Immediate(imm))
    }

    public fun auipc(rd: Register, imm: AsmOperand) {
        requireI()
        emit("auipc", rd, imm)
    }

    public fun jal(rd: Register, target: AsmOperand) {
        requireI()
        emit("jal", rd, target)
    }

    public fun jal(rd: Register, target: String) {
        jal(rd, expr(target))
    }

    public fun jal(target: AsmOperand) {
        jal(ra, target)
    }

    public fun jal(target: String) {
        jal(expr(target))
    }

    public fun jalr(rd: Register, address: Address) {
        requireI()
        emit("jalr", rd, address)
    }

    public fun jalr(rd: Register, rs1: Register, imm: Int = 0) {
        jalr(rd, mem(checkedSigned12("jalr immediate", imm), rs1))
    }

    public fun jalr(rs: Register) {
        emit("jalr", rs)
    }

    public fun beq(rs1: Register, rs2: Register, target: AsmOperand) {
        branch("beq", rs1, rs2, target)
    }

    public fun beq(rs1: Register, rs2: Register, target: String) {
        beq(rs1, rs2, expr(target))
    }

    public fun bne(rs1: Register, rs2: Register, target: AsmOperand) {
        branch("bne", rs1, rs2, target)
    }

    public fun bne(rs1: Register, rs2: Register, target: String) {
        bne(rs1, rs2, expr(target))
    }

    public fun blt(rs1: Register, rs2: Register, target: AsmOperand) {
        branch("blt", rs1, rs2, target)
    }

    public fun blt(rs1: Register, rs2: Register, target: String) {
        blt(rs1, rs2, expr(target))
    }

    public fun bge(rs1: Register, rs2: Register, target: AsmOperand) {
        branch("bge", rs1, rs2, target)
    }

    public fun bge(rs1: Register, rs2: Register, target: String) {
        bge(rs1, rs2, expr(target))
    }

    public fun bltu(rs1: Register, rs2: Register, target: AsmOperand) {
        branch("bltu", rs1, rs2, target)
    }

    public fun bltu(rs1: Register, rs2: Register, target: String) {
        bltu(rs1, rs2, expr(target))
    }

    public fun bgeu(rs1: Register, rs2: Register, target: AsmOperand) {
        branch("bgeu", rs1, rs2, target)
    }

    public fun bgeu(rs1: Register, rs2: Register, target: String) {
        bgeu(rs1, rs2, expr(target))
    }

    public fun lb(rd: Register, address: Address) {
        load("lb", rd, address)
    }

    public fun lh(rd: Register, address: Address) {
        load("lh", rd, address)
    }

    public fun lw(rd: Register, address: Address) {
        load("lw", rd, address)
    }

    public fun lbu(rd: Register, address: Address) {
        load("lbu", rd, address)
    }

    public fun lhu(rd: Register, address: Address) {
        load("lhu", rd, address)
    }

    public fun lb(rd: Register, symbol: AsmOperand) {
        emit("lb", rd, symbol)
    }

    public fun lb(rd: Register, symbol: String) {
        lb(rd, expr(symbol))
    }

    public fun lh(rd: Register, symbol: AsmOperand) {
        emit("lh", rd, symbol)
    }

    public fun lh(rd: Register, symbol: String) {
        lh(rd, expr(symbol))
    }

    public fun lw(rd: Register, symbol: AsmOperand) {
        emit("lw", rd, symbol)
    }

    public fun lw(rd: Register, symbol: String) {
        lw(rd, expr(symbol))
    }

    public fun sb(rs2: Register, address: Address) {
        store("sb", rs2, address)
    }

    public fun sh(rs2: Register, address: Address) {
        store("sh", rs2, address)
    }

    public fun sw(rs2: Register, address: Address) {
        store("sw", rs2, address)
    }

    public fun sb(rs: Register, symbol: AsmOperand, rt: Register) {
        emit("sb", rs, symbol, rt)
    }

    public fun sb(rs: Register, symbol: String, rt: Register) {
        sb(rs, expr(symbol), rt)
    }

    public fun sh(rs: Register, symbol: AsmOperand, rt: Register) {
        emit("sh", rs, symbol, rt)
    }

    public fun sh(rs: Register, symbol: String, rt: Register) {
        sh(rs, expr(symbol), rt)
    }

    public fun sw(rs: Register, symbol: AsmOperand, rt: Register) {
        emit("sw", rs, symbol, rt)
    }

    public fun sw(rs: Register, symbol: String, rt: Register) {
        sw(rs, expr(symbol), rt)
    }

    public fun addi(rd: Register, rs1: Register, imm: Int) {
        iType("addi", rd, rs1, signed12("addi immediate", imm))
    }

    public fun addi(rd: Register, rs1: Register, imm: AsmOperand) {
        iType("addi", rd, rs1, imm)
    }

    public fun slti(rd: Register, rs1: Register, imm: Int) {
        iType("slti", rd, rs1, signed12("slti immediate", imm))
    }

    public fun slti(rd: Register, rs1: Register, imm: AsmOperand) {
        iType("slti", rd, rs1, imm)
    }

    public fun sltiu(rd: Register, rs1: Register, imm: Int) {
        iType("sltiu", rd, rs1, signed12("sltiu immediate", imm))
    }

    public fun sltiu(rd: Register, rs1: Register, imm: AsmOperand) {
        iType("sltiu", rd, rs1, imm)
    }

    public fun xori(rd: Register, rs1: Register, imm: Int) {
        iType("xori", rd, rs1, signed12("xori immediate", imm))
    }

    public fun xori(rd: Register, rs1: Register, imm: AsmOperand) {
        iType("xori", rd, rs1, imm)
    }

    public fun ori(rd: Register, rs1: Register, imm: Int) {
        iType("ori", rd, rs1, signed12("ori immediate", imm))
    }

    public fun ori(rd: Register, rs1: Register, imm: AsmOperand) {
        iType("ori", rd, rs1, imm)
    }

    public fun andi(rd: Register, rs1: Register, imm: Int) {
        iType("andi", rd, rs1, signed12("andi immediate", imm))
    }

    public fun andi(rd: Register, rs1: Register, imm: AsmOperand) {
        iType("andi", rd, rs1, imm)
    }

    public fun slli(rd: Register, rs1: Register, shamt: Int) {
        shift("slli", rd, rs1, shamt)
    }

    public fun srli(rd: Register, rs1: Register, shamt: Int) {
        shift("srli", rd, rs1, shamt)
    }

    public fun srai(rd: Register, rs1: Register, shamt: Int) {
        shift("srai", rd, rs1, shamt)
    }

    public fun add(rd: Register, rs1: Register, rs2: Register) {
        rType("add", rd, rs1, rs2)
    }

    public fun sub(rd: Register, rs1: Register, rs2: Register) {
        rType("sub", rd, rs1, rs2)
    }

    public fun sll(rd: Register, rs1: Register, rs2: Register) {
        rType("sll", rd, rs1, rs2)
    }

    public fun slt(rd: Register, rs1: Register, rs2: Register) {
        rType("slt", rd, rs1, rs2)
    }

    public fun sltu(rd: Register, rs1: Register, rs2: Register) {
        rType("sltu", rd, rs1, rs2)
    }

    public fun xor(rd: Register, rs1: Register, rs2: Register) {
        rType("xor", rd, rs1, rs2)
    }

    public fun srl(rd: Register, rs1: Register, rs2: Register) {
        rType("srl", rd, rs1, rs2)
    }

    public fun sra(rd: Register, rs1: Register, rs2: Register) {
        rType("sra", rd, rs1, rs2)
    }

    public fun or(rd: Register, rs1: Register, rs2: Register) {
        rType("or", rd, rs1, rs2)
    }

    public fun and(rd: Register, rs1: Register, rs2: Register) {
        rType("and", rd, rs1, rs2)
    }

    public fun fence(pred: String = "iorw", succ: String = "iorw") {
        requireI()
        emit("fence", expr(pred), expr(succ))
    }

    public fun ecall() {
        requireI()
        emit("ecall")
    }

    public fun ebreak() {
        requireI()
        emit("ebreak")
    }

    public fun mul(rd: Register, rs1: Register, rs2: Register) {
        mType("mul", rd, rs1, rs2)
    }

    public fun mulh(rd: Register, rs1: Register, rs2: Register) {
        mType("mulh", rd, rs1, rs2)
    }

    public fun mulhsu(rd: Register, rs1: Register, rs2: Register) {
        mType("mulhsu", rd, rs1, rs2)
    }

    public fun mulhu(rd: Register, rs1: Register, rs2: Register) {
        mType("mulhu", rd, rs1, rs2)
    }

    public fun div(rd: Register, rs1: Register, rs2: Register) {
        mType("div", rd, rs1, rs2)
    }

    public fun divu(rd: Register, rs1: Register, rs2: Register) {
        mType("divu", rd, rs1, rs2)
    }

    public fun rem(rd: Register, rs1: Register, rs2: Register) {
        mType("rem", rd, rs1, rs2)
    }

    public fun remu(rd: Register, rs1: Register, rs2: Register) {
        mType("remu", rd, rs1, rs2)
    }

    public fun nop() {
        emit("nop")
    }

    public fun li(rd: Register, value: Int) {
        emit("li", rd, Immediate(value))
    }

    public fun li(rd: Register, value: AsmOperand) {
        emit("li", rd, value)
    }

    public fun li(rd: Register, value: String) {
        li(rd, expr(value))
    }

    public fun mv(rd: Register, rs: Register) {
        emit("mv", rd, rs)
    }

    public fun not(rd: Register, rs: Register) {
        emit("not", rd, rs)
    }

    public fun neg(rd: Register, rs: Register) {
        emit("neg", rd, rs)
    }

    public fun seqz(rd: Register, rs: Register) {
        emit("seqz", rd, rs)
    }

    public fun snez(rd: Register, rs: Register) {
        emit("snez", rd, rs)
    }

    public fun sltz(rd: Register, rs: Register) {
        emit("sltz", rd, rs)
    }

    public fun sgtz(rd: Register, rs: Register) {
        emit("sgtz", rd, rs)
    }

    public fun beqz(rs: Register, target: AsmOperand) {
        emit("beqz", rs, target)
    }

    public fun beqz(rs: Register, target: String) {
        beqz(rs, expr(target))
    }

    public fun bnez(rs: Register, target: AsmOperand) {
        emit("bnez", rs, target)
    }

    public fun bnez(rs: Register, target: String) {
        bnez(rs, expr(target))
    }

    public fun blez(rs: Register, target: AsmOperand) {
        emit("blez", rs, target)
    }

    public fun blez(rs: Register, target: String) {
        blez(rs, expr(target))
    }

    public fun bgez(rs: Register, target: AsmOperand) {
        emit("bgez", rs, target)
    }

    public fun bgez(rs: Register, target: String) {
        bgez(rs, expr(target))
    }

    public fun bltz(rs: Register, target: AsmOperand) {
        emit("bltz", rs, target)
    }

    public fun bltz(rs: Register, target: String) {
        bltz(rs, expr(target))
    }

    public fun bgtz(rs: Register, target: AsmOperand) {
        emit("bgtz", rs, target)
    }

    public fun bgtz(rs: Register, target: String) {
        bgtz(rs, expr(target))
    }

    public fun bgt(rs1: Register, rs2: Register, target: AsmOperand) {
        emit("bgt", rs1, rs2, target)
    }

    public fun bgt(rs1: Register, rs2: Register, target: String) {
        bgt(rs1, rs2, expr(target))
    }

    public fun ble(rs1: Register, rs2: Register, target: AsmOperand) {
        emit("ble", rs1, rs2, target)
    }

    public fun ble(rs1: Register, rs2: Register, target: String) {
        ble(rs1, rs2, expr(target))
    }

    public fun bgtu(rs1: Register, rs2: Register, target: AsmOperand) {
        emit("bgtu", rs1, rs2, target)
    }

    public fun bgtu(rs1: Register, rs2: Register, target: String) {
        bgtu(rs1, rs2, expr(target))
    }

    public fun bleu(rs1: Register, rs2: Register, target: AsmOperand) {
        emit("bleu", rs1, rs2, target)
    }

    public fun bleu(rs1: Register, rs2: Register, target: String) {
        bleu(rs1, rs2, expr(target))
    }

    public fun j(target: AsmOperand) {
        emit("j", target)
    }

    public fun j(target: String) {
        j(expr(target))
    }

    public fun jr(rs: Register) {
        emit("jr", rs)
    }

    public fun ret() {
        emit("ret")
    }

    public fun call(target: AsmOperand) {
        emit("call", target)
    }

    public fun call(target: String) {
        call(expr(target))
    }

    public fun tail(target: AsmOperand) {
        emit("tail", target)
    }

    public fun tail(target: String) {
        tail(expr(target))
    }

    public fun la(rd: Register, target: AsmOperand) {
        emit("la", rd, target)
    }

    public fun la(rd: Register, target: String) {
        la(rd, expr(target))
    }

    public fun lla(rd: Register, target: AsmOperand) {
        emit("lla", rd, target)
    }

    public fun lla(rd: Register, target: String) {
        lla(rd, expr(target))
    }

    public fun lga(rd: Register, target: AsmOperand) {
        emit("lga", rd, target)
    }

    public fun lga(rd: Register, target: String) {
        lga(rd, expr(target))
    }

    public fun unimp() {
        emit("unimp")
    }

    private fun branch(mnemonic: String, rs1: Register, rs2: Register, target: AsmOperand) {
        requireI()
        emit(mnemonic, rs1, rs2, target)
    }

    private fun load(mnemonic: String, rd: Register, address: Address) {
        requireI()
        validateAddressImmediate(mnemonic, address)
        emit(mnemonic, rd, address)
    }

    private fun store(mnemonic: String, rs2: Register, address: Address) {
        requireI()
        validateAddressImmediate(mnemonic, address)
        emit(mnemonic, rs2, address)
    }

    private fun iType(mnemonic: String, rd: Register, rs1: Register, operand: AsmOperand) {
        requireI()
        emit(mnemonic, rd, rs1, operand)
    }

    private fun shift(mnemonic: String, rd: Register, rs1: Register, shamt: Int) {
        requireI()
        require(shamt in 0..31) { "$mnemonic shift amount for RV32 must be in 0..31, got $shamt." }
        emit(mnemonic, rd, rs1, Immediate(shamt))
    }

    private fun rType(mnemonic: String, rd: Register, rs1: Register, rs2: Register) {
        requireI()
        emit(mnemonic, rd, rs1, rs2)
    }

    private fun mType(mnemonic: String, rd: Register, rs1: Register, rs2: Register) {
        requireM()
        emit(mnemonic, rd, rs1, rs2)
    }

    private fun requireI() {
        require(target.supports(RiscvExtension.I)) { "Target $target does not support the RISC-V I extension." }
    }

    private fun requireM() {
        require(target.supports(RiscvExtension.M)) { "Target $target does not support the RISC-V M extension." }
    }

    private fun validateAddressImmediate(mnemonic: String, address: Address) {
        val immediate = address.offset as? Immediate ?: return
        checkedSigned12("$mnemonic offset", immediate.value)
    }

    private fun checkedSigned12(name: String, value: Int): Int {
        require(value in -2048..2047) { "$name must fit in signed 12 bits, got $value." }
        return value
    }

    private fun signed12(name: String, value: Int): Immediate = Immediate(checkedSigned12(name, value))
}
