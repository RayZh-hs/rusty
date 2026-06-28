package rusty.opt.passes

import space.norb.llvm.analysis.AnalysisManager
import space.norb.llvm.core.ValueUseRegistry
import space.norb.llvm.instructions.base.Instruction
import space.norb.llvm.instructions.base.TerminatorInst
import space.norb.llvm.instructions.memory.StoreInst
import space.norb.llvm.instructions.other.CallInst
import space.norb.llvm.structure.Module
import space.norb.llvm.transformation.IRPass
import java.util.IdentityHashMap

/**
 * Mark-and-sweep dead code elimination.
 *
 * The existing InstCombine cleanup only removes instructions whose use list is empty, which cannot
 * break self-referential cycles. Mem2Reg routinely produces such cycles: a variable that is declared
 * inside a loop and reassigned on every iteration before use ends up with a loop-header phi whose only
 * consumer is the matching back-edge phi (and vice-versa). The values are never observed by any
 * side-effecting instruction, yet each cycle still consumes a register and a back-edge copy per
 * iteration. On the bytecode-VM interpreter this left ~16 dead loop-carried phi pairs in the hot loop.
 *
 * This pass seeds liveness from the instructions that are observable — terminators, stores and calls —
 * then propagates backwards through operands to a fixpoint. Anything not reached is dead (it can only be
 * used by other dead instructions) and is removed. Removing a value that no live instruction observes
 * cannot change program behavior, so the transform is sound by construction.
 */
object AggressiveDeadCodeEliminationPass : IRPass() {
    override fun run(module: Module, am: AnalysisManager): Module {
        for (function in module.functions) {
            if (function.basicBlocks.isEmpty()) continue

            val live: MutableSet<Instruction> =
                java.util.Collections.newSetFromMap(IdentityHashMap<Instruction, Boolean>())
            val worklist = ArrayDeque<Instruction>()

            for (block in function.basicBlocks) {
                for (instruction in block.instructions) {
                    if (isObservable(instruction) && live.add(instruction)) {
                        worklist.addLast(instruction)
                    }
                }
            }

            while (worklist.isNotEmpty()) {
                val instruction = worklist.removeLast()
                for (operand in instruction.getOperandsList()) {
                    if (operand is Instruction && live.add(operand)) {
                        worklist.addLast(operand)
                    }
                }
            }

            for (block in function.basicBlocks) {
                val dead = block.instructions.filter { it !in live }
                if (dead.isEmpty()) continue
                for (instruction in dead) detachOperands(instruction)
                block.instructions.removeAll(dead.toHashSet())
            }
        }
        return module
    }

    /** Instructions whose results may be observed by program execution. These seed the live set. */
    private fun isObservable(instruction: Instruction): Boolean =
        when (instruction) {
            is TerminatorInst -> true
            is StoreInst -> true
            is CallInst -> true
            else -> false
        }

    private fun detachOperands(instruction: Instruction) {
        for (index in 0 until instruction.getNumOperands()) {
            ValueUseRegistry.unregisterUse(instruction.getOperand(index), instruction)
        }
    }
}
