package rusty.asm.support

import space.norb.riscv.Address
import space.norb.riscv.AssemblyLine
import space.norb.riscv.AssemblyProgram
import space.norb.riscv.InstructionLine
import space.norb.riscv.LabelLine
import space.norb.riscv.Register

/**
 * Stage 2 of register copy coalescing: an asm-level peephole over the emitted RISC-V program.
 *
 * Where [RegallocCoalescing] removes phi-resolution copies the allocator can see, this stage removes
 * the codegen-artifact moves it cannot: gep/load lowering computes into a fixed scratch register
 * (t3-t6) and then moves the result into the allocated destination, leaving a `mv` after almost every
 * address computation and load. Two rewrites, iterated to a fixpoint (each exposes work for the other):
 *
 *   R1 result-forwarding:        OP t5, a, b ; mv rd, t5   ->   OP rd, a, b
 *   R2 scratch copy-propagation: mv t4, rs ; ... use t4    ->   ... use rs   (then the dead copy is dropped)
 *
 * Both are valid because of the codegen invariant that scratch t3-t6 are never live across an
 * instruction-selection boundary — each is written and consumed within the lowering of a single IR
 * instruction. R1 also relies on the producer and move being adjacent (a RISC-V op reads all sources
 * before writing its destination), and is guarded by a scan proving the scratch is dead after the move.
 *
 * Note: any mnemonic the classifier does not positively recognize is treated as a barrier, so unknown
 * instructions only stop optimization — they can never be miscompiled.
 */
object AsmCoalescing {
    private const val T3 = 28
    private const val T4 = 29
    private const val T5 = 30
    private const val T6 = 31
    private fun Register.isScratch(): Boolean = number in T3..T6

    fun optimize(program: AssemblyProgram): AssemblyProgram {
        val lines = program.lines.toMutableList()
        // R1 and R2 expose further opportunities for each other (R1 removes the trailing move so R2 can
        // see the scratch copy feeding the surviving instruction); iterate to a fixpoint.
        var iterations = 0
        while (iterations < 8) {
            val r1 = forwardResultMoves(lines)
            val r2 = propagateScratchCopies(lines)
            if (!r1 && !r2) break
            iterations++
        }
        removeSelfMoves(lines)
        return AssemblyProgram(lines)
    }

    private fun removeSelfMoves(lines: MutableList<AssemblyLine>) {
        lines.removeAll { line ->
            line is InstructionLine && line.mnemonic == "mv" &&
                (line.operands.getOrNull(0) as? Register) == (line.operands.getOrNull(1) as? Register) &&
                line.operands.getOrNull(0) is Register
        }
    }

    // ---- R1 -----------------------------------------------------------------------------------------

    private fun forwardResultMoves(lines: MutableList<AssemblyLine>): Boolean {
        var changed = false
        var i = 0
        while (i < lines.size - 1) {
            val producer = lines[i] as? InstructionLine
            val move = lines[i + 1] as? InstructionLine
            if (producer != null && move != null && move.mnemonic == "mv") {
                val rd = move.operands.getOrNull(0) as? Register
                val rs = move.operands.getOrNull(1) as? Register
                val dest = destinationRegister(producer)
                if (rd != null && rs != null && dest != null && dest == rs && rs.isScratch() && rd != rs &&
                    scratchDeadAfter(lines, i + 2, rs)
                ) {
                    lines[i] = producer.copy(operands = listOf(rd) + producer.operands.drop(1))
                    lines.removeAt(i + 1)
                    changed = true
                    continue
                }
            }
            i++
        }
        return changed
    }

    /** True if scratch register [rs] is not read again before being redefined or leaving the block. */
    private fun scratchDeadAfter(lines: List<AssemblyLine>, from: Int, rs: Register): Boolean {
        var i = from
        while (i < lines.size) {
            when (val line = lines[i]) {
                is LabelLine -> return true
                is InstructionLine -> {
                    if (readsRegister(line, rs)) return false
                    if (isBlockExit(line)) return true
                    if (destinationRegister(line) == rs) return true
                }
                else -> {}
            }
            i++
        }
        return true
    }

    // ---- R2 -----------------------------------------------------------------------------------------

    private fun propagateScratchCopies(lines: MutableList<AssemblyLine>): Boolean {
        var changed = false
        var i = 0
        while (i < lines.size) {
            val copy = lines[i] as? InstructionLine
            if (copy != null && copy.mnemonic == "mv") {
                val td = copy.operands.getOrNull(0) as? Register
                val rs = copy.operands.getOrNull(1) as? Register
                if (td != null && rs != null && td.isScratch() && td != rs && tryPropagate(lines, i, td, rs)) {
                    lines.removeAt(i)
                    changed = true
                    continue
                }
            }
            i++
        }
        return changed
    }

    /**
     * Attempt to replace every in-block use of scratch [td] (defined by the copy at [copyIndex]) with
     * [rs]. Returns true only if all uses up to td's next definition / block exit were rewritten while rs
     * stayed unmodified — i.e. the copy is provably dead afterwards and safe to delete.
     */
    private fun tryPropagate(lines: MutableList<AssemblyLine>, copyIndex: Int, td: Register, rs: Register): Boolean {
        val rewrites = mutableListOf<Pair<Int, InstructionLine>>()
        var i = copyIndex + 1
        while (i < lines.size) {
            when (val line = lines[i]) {
                is LabelLine -> { applyRewrites(lines, rewrites); return true } // td dead at block boundary
                is InstructionLine -> {
                    val redefinesRs = writesRegister(line, rs)
                    if (readsRegister(line, td)) {
                        // Substituting rs is only valid while rs still holds the copied value. If this
                        // very instruction also redefines rs we conservatively bail.
                        if (redefinesRs) return false
                        rewrites.add(i to substitute(line, td, rs))
                    }
                    if (destinationRegister(line) == td) { applyRewrites(lines, rewrites); return true } // td redefined
                    if (isBlockExit(line)) { applyRewrites(lines, rewrites); return true }
                    if (redefinesRs) {
                        // rs changes from here on; remaining td uses (if any) would read a stale rs.
                        return if (laterReadsBeforeRedef(lines, i + 1, td)) false
                        else { applyRewrites(lines, rewrites); true }
                    }
                }
                else -> {}
            }
            i++
        }
        applyRewrites(lines, rewrites)
        return true
    }

    private fun laterReadsBeforeRedef(lines: List<AssemblyLine>, from: Int, reg: Register): Boolean {
        var i = from
        while (i < lines.size) {
            when (val line = lines[i]) {
                is LabelLine -> return false
                is InstructionLine -> {
                    if (readsRegister(line, reg)) return true
                    if (isBlockExit(line)) return false
                    if (destinationRegister(line) == reg) return false
                }
                else -> {}
            }
            i++
        }
        return false
    }

    private fun applyRewrites(lines: MutableList<AssemblyLine>, rewrites: List<Pair<Int, InstructionLine>>) {
        for ((index, replacement) in rewrites) lines[index] = replacement
    }

    /** Replace [from] with [to] in source positions only; the destination operand is left untouched. */
    private fun substitute(line: InstructionLine, from: Register, to: Register): InstructionLine {
        val skipFirst = line.mnemonic in destinationFirst
        val operands = line.operands.mapIndexed { index, operand ->
            when (operand) {
                is Register -> if (operand == from && !(skipFirst && index == 0)) to else operand
                is Address -> if (operand.base == from) operand.copy(base = to) else operand
                else -> operand
            }
        }
        return line.copy(operands = operands)
    }

    // ---- instruction classification -----------------------------------------------------------------

    /** Mnemonics whose operand[0] is a register that is purely written (no read of the old value). */
    private val destinationFirst: Set<String> = setOf(
        "mv", "li", "lui", "la", "auipc", "neg", "negw", "not", "seqz", "snez", "sltz", "sgtz",
        "sext.w", "sext.b", "sext.h", "zext.w", "zext.b", "zext.h", "mov",
        "add", "addw", "addi", "addiw", "sub", "subw", "mul", "mulw", "mulh", "mulhu", "mulhsu",
        "div", "divw", "divu", "divuw", "rem", "remw", "remu", "remuw",
        "and", "andi", "or", "ori", "xor", "xori",
        "sll", "sllw", "slli", "slliw", "srl", "srlw", "srli", "srliw", "sra", "sraw", "srai", "sraiw",
        "slt", "slti", "sltu", "sltiu",
        "lb", "lbu", "lh", "lhu", "lw", "lwu", "ld",
    )

    /** Mnemonics that read all their register operands and write none of them. */
    private val readsOnly: Set<String> = setOf(
        "sb", "sh", "sw", "sd",
        "beq", "bne", "blt", "bge", "bltu", "bgeu",
        "beqz", "bnez", "blez", "bgez", "bltz", "bgtz",
    )

    /** Control transfers that end a straight-line block and reference no reserved scratch register. */
    private val controlExit: Set<String> = setOf(
        "j", "jal", "jalr", "jr", "call", "tail", "ret", "nop", "ecall", "ebreak", "fence",
    )

    private fun destinationRegister(line: InstructionLine): Register? =
        if (line.mnemonic in destinationFirst) line.operands.getOrNull(0) as? Register else null

    /** Sound over-approximation: true if [reg] may be read by [line]. */
    private fun readsRegister(line: InstructionLine, reg: Register): Boolean {
        val skipFirst = line.mnemonic in destinationFirst
        line.operands.forEachIndexed { index, operand ->
            when (operand) {
                is Register -> if (operand == reg && !(skipFirst && index == 0)) return true
                is Address -> if (operand.base == reg) return true
                else -> {}
            }
        }
        return false
    }

    /** Sound over-approximation: true if [line] may modify [reg]. */
    private fun writesRegister(line: InstructionLine, reg: Register): Boolean {
        val mnemonic = line.mnemonic
        if (mnemonic in destinationFirst) return destinationRegister(line) == reg
        if (mnemonic in readsOnly || mnemonic in controlExit) return false
        return true // unknown mnemonic: assume it clobbers reg
    }

    private fun isBlockExit(line: InstructionLine): Boolean {
        val mnemonic = line.mnemonic
        if (mnemonic in destinationFirst) return false
        if (mnemonic in readsOnly) return mnemonic.startsWith("b") // branches end the block; stores do not
        return true // controlExit or anything unrecognized
    }
}
